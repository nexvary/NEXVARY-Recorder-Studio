package com.nexvary.recorder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.camera.BackgroundCameraService
import com.nexvary.recorder.delivery.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Compiled only for -PguardianMailReleaseTest=true. No external SMTP traffic. */
@RunWith(AndroidJUnit4::class)
class ReleaseMailWorkerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var active = false
    @Before fun prepare() {
        Assume.assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish")); active = true
        assertFalse("Must exercise production BuildConfig", BuildConfig.DEBUG)
        SmtpSettings(context).clear()
    }
    @After fun clean() { if (active) SmtpSettings(context).clear() }
    private fun awaitState(timeout: Long = 35_000, ready: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) { if (ready()) return; SystemClock.sleep(100) }
        fail("Release state transition timed out")
    }
    @Test fun releaseWorkerProcessesApprovedJobWithoutDebugGate() {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "release-worker-${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(1024)) }
        val queue = MailQueue(context); val job = queue.enqueue(file, "one@example.invalid,two@example.invalid", 25)
        try {
            MailWorker.schedule(context, job.id)
            awaitState { queue.get(job.id)?.state == MailQueue.State.CONFIG }
            assertTrue(queue.attachment(job.id).isFile); assertTrue(file.isFile)
        } finally { queue.cancel(job.id); file.delete() }
    }
    @Test fun releaseTimedCameraQueuesOneCopyForTwoRecipientsAndRetainsOriginal() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val queue = MailQueue(context); val before = queue.jobs().map { it.id }.toSet()
        var output: File? = null
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(activity, Intent(activity, BackgroundCameraService::class.java)
                        .setAction(BackgroundCameraService.ACTION_START)
                        .putExtra(BackgroundCameraService.EXTRA_DURATION, 5_000L)
                        .putExtra(BackgroundCameraService.EXTRA_RECIPIENT, "one@example.invalid;two@example.invalid"))
                }
                awaitState { BackgroundCameraService.state.stage == BackgroundCameraService.Companion.Stage.RECORDING }
                awaitState { BackgroundCameraService.state.stage in listOf(BackgroundCameraService.Companion.Stage.SAVED, BackgroundCameraService.Companion.Stage.ERROR) }
                val saved = BackgroundCameraService.state
                assertEquals(BackgroundCameraService.Companion.Stage.SAVED, saved.stage)
                assertTrue(saved.mailRequested); assertTrue(saved.mailQueued)
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), saved.name); output = file
                val metadata = android.media.MediaMetadataRetriever()
                try {
                    metadata.setDataSource(file.absolutePath)
                    assertEquals("yes", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                    assertTrue(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 3_000..8_000)
                } finally { metadata.release() }
                val job = queue.jobs().single { it.id !in before }
                assertEquals("one@example.invalid, two@example.invalid", job.recipient)
                awaitState { queue.get(job.id)?.state == MailQueue.State.CONFIG }
                val assets = instrumentation.context.assets
                val key = assets.open("smtp-test-key.b64").use { it.readBytes() }
                val cert = assets.open("smtp-test-cert.b64").use { it.readBytes() }
                LocalSmtpFixture(key, cert, true).use { server ->
                    queue.retry(job.id, 25, job.recipient)
                    val profile = SmtpProfile("127.0.0.1", server.port, true, "fixture-user", "fixture-password", "sender@example.invalid", 25)
                    assertEquals(MailQueue.State.SENT, MailDispatcher(queue, SmtpSender("Local only", "Synthetic capture", server.factory)).deliver(job.id, profile))
                    assertEquals(listOf("one@example.invalid", "two@example.invalid"), server.recipients.toList())
                    assertEquals(1, server.dataCommands)
                    val mime = jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()), java.io.ByteArrayInputStream(server.received))
                    assertArrayEquals(file.readBytes(), (mime.content as jakarta.mail.internet.MimeMultipart).getBodyPart(1).inputStream.readBytes())
                    assertTrue(file.isFile); assertFalse(queue.attachment(job.id).exists())
                }
            }
        } finally {
            context.stopService(Intent(context, BackgroundCameraService::class.java))
            queue.jobs().filter { it.id !in before }.forEach { queue.cancel(it.id) }
            output?.delete()
        }
    }
}
