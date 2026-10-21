package com.nexvary.recorder

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.camera.BackgroundCameraService
import com.nexvary.recorder.camera.CameraQuality
import com.nexvary.recorder.delivery.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.SocketTimeoutException

/** All SMTP attempts here use an in-process fake transport. No external recipient is ever contacted. */
@RunWith(AndroidJUnit4::class)
class RecordingDeliveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val profile = SmtpProfile("smtp.example.invalid", 587, false, "fixture", "fixture-secret-only", "sender@example.invalid", 25)
    private fun isolated(block: (Context, File) -> Unit) {
        val root = File(context.cacheDir, "delivery-test-${System.nanoTime()}").apply { mkdirs() }
        val privateDir = File(root, "private").apply { mkdirs() }
        val movies = File(root, "Movies").apply { mkdirs() }
        val wrapper = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = privateDir
            override fun getExternalFilesDir(type: String?) = movies
        }
        try { block(wrapper, movies) } finally { root.deleteRecursively() }
    }
    @Test fun androidRuntimeSendsBinaryAttachmentThroughRealTlsAndStartTlsOnLoopback() {
        val assets = instrumentation.context.assets
        val key = assets.open("smtp-test-key.b64").use { it.readBytes() }
        val certificate = assets.open("smtp-test-cert.b64").use { it.readBytes() }
        for (direct in listOf(true, false)) LocalSmtpFixture(key, certificate, direct).use { server ->
            isolated { app, movies ->
                val source = File(movies, "fixture.mp4").apply { writeBytes(ByteArray(40_001) { (it % 251).toByte() }) }
                val queue = MailQueue(app)
                val job = queue.enqueue(source, "recipient@example.invalid", 25)
                val sender = SmtpSender("Local fixture", "No external email", server.factory)
                val diagnosticTransport = object : RecordingMailTransport by sender {
                    override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) {
                        try { sender.send(profile, job, file, beforeData) }
                        catch (e: Exception) { throw AssertionError("Loopback SMTP runtime failed (direct TLS=$direct)", e) }
                    }
                }
                assertEquals(MailQueue.State.SENT, MailDispatcher(queue, diagnosticTransport).deliver(job.id,
                    SmtpProfile("127.0.0.1", server.port, direct, "fixture-user", "fixture-password", "sender@example.invalid", 25)))
                assertTrue(source.isFile); assertFalse(queue.attachment(job.id).exists())
                val mime = jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()), java.io.ByteArrayInputStream(server.received))
                val multipart = mime.content as jakarta.mail.internet.MimeMultipart
                assertArrayEquals(source.readBytes(), multipart.getBodyPart(1).inputStream.readBytes())
            }
        }
    }
    @Test fun smtpCredentialsAreEncryptedAndRemovable() = isolated { app, _ ->
        val settings = SmtpSettings(app); settings.save(profile)
        assertEquals(profile, SmtpSettings(app).load())
        val bytes = File(app.noBackupFilesDir, "smtp-profile.aes").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains(profile.password)); assertFalse(bytes.contains(profile.username)); assertFalse(bytes.contains(profile.host))
        settings.clear(); assertNull(settings.load())
    }
    @Test fun acceptedMailDeletesOnlyPrivateSendCopyAndCannotBeSentTwice() = isolated { app, movies ->
        val queue = MailQueue(app); val original = File(movies, "test.mp4").apply { writeBytes(ByteArray(8192) { it.toByte() }) }
        val bytes = original.readBytes(); val job = queue.enqueue(original, "recipient@example.invalid", 25)
        // A library move/delete after enqueue cannot corrupt the already opted-in send copy.
        original.delete()
        var sends = 0
        val fake = object : RecordingMailTransport {
            override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) { beforeData(); assertArrayEquals(bytes, file.readBytes()); sends++ }
            override fun close() { }
        }
        val dispatcher = MailDispatcher(queue, fake)
        assertEquals(MailQueue.State.SENT, dispatcher.deliver(job.id, profile))
        assertEquals(MailQueue.State.SENT, dispatcher.deliver(job.id, profile)); assertEquals(1, sends)
        assertFalse(queue.attachment(job.id).exists())
    }
    @Test fun retryIsBoundedAndAnUncertainDataOutcomeNeedsManualApproval() = isolated { app, movies ->
        val queue = MailQueue(app); val original = File(movies, "test.mp4").apply { writeBytes(ByteArray(1024)) }
        val job = queue.enqueue(original, "recipient@example.invalid", 25)
        var afterData = false
        val fake = object : RecordingMailTransport {
            override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) {
                if (afterData) beforeData()
                throw SocketTimeoutException("Local simulation")
            }
            override fun close() { }
        }
        val dispatcher = MailDispatcher(queue, fake)
        assertEquals(MailQueue.State.RETRY, dispatcher.deliver(job.id, profile))
        assertEquals(MailQueue.State.RETRY, dispatcher.deliver(job.id, profile))
        assertEquals(MailQueue.State.FAILED, dispatcher.deliver(job.id, profile))
        queue.retry(job.id, 25); afterData = true
        assertEquals(MailQueue.State.UNCERTAIN, dispatcher.deliver(job.id, profile))
        assertEquals(MailQueue.State.UNCERTAIN, dispatcher.deliver(job.id, profile))
        assertTrue(queue.attachment(job.id).exists()); assertTrue(original.isFile)
        assertTrue(queue.cancel(job.id)); assertFalse(queue.attachment(job.id).exists()); assertTrue(original.isFile)
    }
    @Test fun negativeSmtpCompletionCodeIsUncertainAndCorruptCopyCanBeRebuiltFromOriginal() = isolated { app, movies ->
        val queue = MailQueue(app)
        val source = File(movies, "repair.mp4").apply { writeBytes(ByteArray(4096) { (it % 239).toByte() }) }
        val job = queue.enqueue(source, "recipient@example.invalid", 25)
        queue.attachment(job.id).writeBytes(ByteArray(4096))
        var called = false
        val fake = object : RecordingMailTransport {
            override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) {
                called = true; beforeData()
                throw org.eclipse.angus.mail.smtp.SMTPSendFailedException(".", -1, "Local interruption", null, null, null, null)
            }
            override fun close() { }
        }
        val dispatcher = MailDispatcher(queue, fake)
        assertEquals(MailQueue.State.MISSING, dispatcher.deliver(job.id, profile)); assertFalse(called)
        assertEquals(MailQueue.State.QUEUED, queue.retry(job.id, 25)?.state)
        assertArrayEquals(source.readBytes(), queue.attachment(job.id).readBytes())
        assertEquals(MailQueue.State.UNCERTAIN, dispatcher.deliver(job.id, profile)); assertTrue(source.exists())
    }
    @Test fun sizeLimitAndInterruptedWorkerDoNotDeleteOrAutomaticallyResend() = isolated { app, movies ->
        val queue = MailQueue(app); val source = File(movies, "large.mp4")
        java.io.RandomAccessFile(source, "rw").use { it.setLength(2_000_000) }
        val job = queue.enqueue(source, "recipient@example.invalid", 1)
        assertEquals(MailQueue.State.LIMIT, job.state); assertFalse(queue.attachment(job.id).exists())
        assertEquals(MailQueue.State.QUEUED, queue.retry(job.id, 5)?.state)
        assertNotNull(queue.claim(job.id)); queue.interrupted(job.id)
        assertEquals(MailQueue.State.UNCERTAIN, queue.get(job.id)?.state); assertNull(queue.claim(job.id))
        assertTrue(source.isFile); assertTrue(queue.attachment(job.id).isFile)
    }
    @Test fun cameraValidatesTimerAndEmailBeforeRequestingCaptureAndKeepsDraftOnRotation() {
        SmtpSettings(context).clear()
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
            onView(withId(R.id.cameraTimer)).perform(reveal(), click())
            onView(withId(R.id.cameraMinutes)).perform(reveal(), replaceText("0"), closeSoftKeyboard())
            onView(withId(R.id.cameraStart)).perform(reveal(), click())
            onView(withId(R.id.cameraStatus)).perform(reveal()).check(matches(withText(R.string.camera_minutes_invalid)))
            onView(withId(R.id.cameraMinutes)).perform(reveal(), replaceText("١٠"), closeSoftKeyboard())
            onView(withId(R.id.cameraEmail)).perform(reveal(), click())
            onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText("bad"), closeSoftKeyboard())
            onView(withId(R.id.cameraStart)).perform(reveal(), click())
            onView(withId(R.id.cameraStatus)).perform(reveal()).check(matches(withText(R.string.camera_recipient_invalid)))
            onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText("qa@example.invalid"), closeSoftKeyboard())
            scenario.recreate(); awaitUi(withId(R.id.cameraRecipient))
            onView(withId(R.id.cameraRecipient)).perform(reveal()).check(matches(withText("qa@example.invalid")))
            onView(withId(R.id.cameraStart)).perform(reveal(), click())
            onView(withId(R.id.cameraStatus)).perform(reveal()).check(matches(withText(R.string.mail_config)))
            onView(withId(R.id.openSmtp)).perform(reveal(), click()); awaitUi(withId(R.id.smtpHost))
            onView(withId(R.id.smtpSave)).perform(reveal(), click())
            onView(withId(R.id.smtpStatus)).perform(reveal()).check(matches(withText(R.string.smtp_host_invalid)))
            onView(withId(R.id.btnBack)).perform(reveal(), click())
        }
        assertFalse(BackgroundCameraService.state.stage == BackgroundCameraService.Companion.Stage.RECORDING)
    }
    private fun awaitCapture(timeoutMs: Long = 35_000, predicate: (BackgroundCameraService.Companion.CaptureState) -> Boolean): BackgroundCameraService.Companion.CaptureState {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            val state = BackgroundCameraService.state
            if (predicate(state)) return state
            SystemClock.sleep(50)
        }
        throw AssertionError("Camera state timeout: ${BackgroundCameraService.state}")
    }
    @Test fun oneMinuteCompactCaptureWithAudioIsPlayableSmallAndSavesWithScreenOff() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        var output: File? = null
        val before = MailQueue(context).jobs().size
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.cameraTimer))
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(activity, Intent(activity, BackgroundCameraService::class.java)
                        .setAction(BackgroundCameraService.ACTION_START)
                        .putExtra(BackgroundCameraService.EXTRA_AUDIO, true)
                        .putExtra(BackgroundCameraService.EXTRA_QUALITY, CameraQuality.COMPACT.key)
                        .putExtra(BackgroundCameraService.EXTRA_DURATION, 60_000L))
                }
                awaitCapture { it.stage == BackgroundCameraService.Companion.Stage.RECORDING }
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
                val power = context.getSystemService(android.os.PowerManager::class.java)
                val sleepDeadline = SystemClock.uptimeMillis() + 2_000
                while (power.isInteractive && SystemClock.uptimeMillis() < sleepDeadline) SystemClock.sleep(50)
                assertFalse(power.isInteractive)
                val saved = awaitCapture(90_000) { it.stage == BackgroundCameraService.Companion.Stage.SAVED || it.stage == BackgroundCameraService.Companion.Stage.ERROR }
                assertEquals(BackgroundCameraService.Companion.Stage.SAVED, saved.stage)
                output = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), saved.name)
                val video = output!!
                val metadata = android.media.MediaMetadataRetriever()
                val duration: Long
                try {
                    metadata.setDataSource(video.absolutePath)
                    duration = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("duration=$duration", duration in 55_000..66_000)
                    assertEquals("yes", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                    assertEquals("yes", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                } finally { metadata.release() }
                val extractor = android.media.MediaExtractor()
                var width = 0; var height = 0; var audioTracks = 0
                try {
                    extractor.setDataSource(video.absolutePath)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        when (format.getString(android.media.MediaFormat.KEY_MIME)) {
                            "video/avc" -> { width = format.getInteger(android.media.MediaFormat.KEY_WIDTH); height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT) }
                            "audio/mp4a-latm" -> { audioTracks++; assertEquals(1, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)) }
                        }
                    }
                } finally { extractor.release() }
                assertTrue(width > 0 && height > 0 && maxOf(width,height) <= 640 && minOf(width,height) <= 480)
                assertEquals(1, audioTracks)
                assertTrue("Actual minute bytes=${video.length()}", video.length() in 1..4_500_000)
                assertEquals(before, MailQueue(context).jobs().size)
                File(context.getExternalFilesDir(null), "quality-measurements.json").writeText(org.json.JSONObject()
                    .put("profile", "compact").put("durationMs", duration).put("bytes", video.length())
                    .put("width", width).put("height", height).put("audioTracks", audioTracks)
                    .put("mimeMessageBytes", DeliveryPolicy.messageBytes(video.length())).toString(2))
            }
        } finally {
            context.stopService(Intent(context, BackgroundCameraService::class.java))
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            output?.delete()
        }
    }
    @Test fun nativeTimerSavesPlayableVideoWithActivityStoppedAndScreenOff() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        var output: File? = null
        val queuedBefore = MailQueue(context).jobs().size
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.cameraTimer))
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(activity, Intent(activity, BackgroundCameraService::class.java)
                        .setAction(BackgroundCameraService.ACTION_START).putExtra(BackgroundCameraService.EXTRA_DURATION, 5_000L))
                }
                awaitCapture { it.stage == BackgroundCameraService.Companion.Stage.RECORDING }
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
                val power = context.getSystemService(android.os.PowerManager::class.java)
                val sleepDeadline = SystemClock.uptimeMillis() + 2_000
                while (power.isInteractive && SystemClock.uptimeMillis() < sleepDeadline) SystemClock.sleep(50)
                assertFalse("Screen must actually be off during the timer test", power.isInteractive)
                val saved = awaitCapture { it.stage == BackgroundCameraService.Companion.Stage.SAVED || it.stage == BackgroundCameraService.Companion.Stage.ERROR }
                assertEquals(BackgroundCameraService.Companion.Stage.SAVED, saved.stage)
                assertFalse(saved.mailQueued)
                output = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), saved.name)
                val metadata = android.media.MediaMetadataRetriever()
                try {
                    metadata.setDataSource(output!!.absolutePath)
                    val duration = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("duration=$duration", duration in 3_000..8_000)
                    assertEquals("yes", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                } finally { metadata.release() }
                assertEquals(queuedBefore, MailQueue(context).jobs().size)
            }
        } finally {
            context.stopService(Intent(context, BackgroundCameraService::class.java))
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            output?.delete()
        }
    }
    @Test fun manualStopSavesPlayableVideoAndAllowsImmediateNextRecordingWithoutEmail() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val outputs = mutableListOf<File>()
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.cameraTimer))
                repeat(2) {
                    scenario.onActivity { activity -> ContextCompat.startForegroundService(activity,
                        Intent(activity, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)) }
                    awaitCapture { it.stage == BackgroundCameraService.Companion.Stage.RECORDING }
                    SystemClock.sleep(2_000)
                    scenario.onActivity { activity -> activity.startService(Intent(activity, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP)) }
                    val saved = awaitCapture { it.stage == BackgroundCameraService.Companion.Stage.SAVED || it.stage == BackgroundCameraService.Companion.Stage.ERROR }
                    assertEquals(BackgroundCameraService.Companion.Stage.SAVED, saved.stage); assertFalse(saved.mailRequested)
                    val output = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), saved.name)
                    outputs.add(output)
                    assertTrue(output.length() > 0)
                    val metadata = android.media.MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(output.absolutePath)
                        assertEquals("yes", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                        assertTrue(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 500)
                    } finally { metadata.release() }
                }
            }
        } finally { context.stopService(Intent(context, BackgroundCameraService::class.java)); outputs.forEach { it.delete() } }
    }
}
