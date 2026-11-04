package com.nexvary.recorder

import android.Manifest
import android.content.ContextWrapper
import android.content.Intent
import android.media.MediaExtractor
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.delivery.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Emulated camera plus synthetic mail; cannot establish physical/OEM or inbox reliability. */
@RunWith(AndroidJUnit4::class)
class ContinuousRecordingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun await(timeout: Long = 15_000, predicate: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + timeout
        while (!predicate() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue("Timed out waiting for continuous capture", predicate())
    }
    @Test fun continuousCameraFinalizesAndQueuesDuringNextClipAndKeepsFinalTail() {
        Assume.assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val long = InstrumentationRegistry.getArguments().getString("continuousLong") == "true"
        val seconds = if (long) 60 else 30
        val runMs = if (long) 900_000L else 65_000L
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.CAMERA}").close()
        val idsBefore = MailQueue(context).jobs().map { it.id }.toSet()
        var id = ""
        val scenario = ActivityScenario.launch(BackgroundCameraActivity::class.java)
        try {
            scenario.onActivity {
                ContextCompat.startForegroundService(it, Intent(it, BackgroundCameraService::class.java)
                    .setAction(BackgroundCameraService.ACTION_START)
                    .putExtra(BackgroundCameraService.EXTRA_CONTINUOUS, true)
                    .putExtra(BackgroundCameraService.EXTRA_SEGMENT_SECONDS, seconds)
                    .putExtra(BackgroundCameraService.EXTRA_DURATION, 1000L) // Must NOT stop this session.
                    .putExtra(BackgroundCameraService.EXTRA_RECIPIENT, "fixture@example.invalid"))
            }
            await { BackgroundCameraService.activeContinuousSession != null }
            id = requireNotNull(BackgroundCameraService.activeContinuousSession)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            await(seconds * 1000L + 20_000) {
                BackgroundCameraService.state.segmentNumber >= 2 && MailQueue(context).jobs().any { it.id !in idsBefore }
            }
            assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
            val queue = MailQueue(context)
            val first = queue.jobs().single { it.id == SegmentTimeline.jobId(id, 1) }
            assertTrue(File(first.original).isFile)
            // The automatic worker has no configured provider in this fixture. Confirm retry
            // identity and independent dispatch using a synthetic acknowledging transport.
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("recording-mail-${first.id}").result.get()
            await { queue.get(first.id)?.state != MailQueue.State.SENDING }
            if (queue.get(first.id)?.state !in listOf(MailQueue.State.QUEUED, MailQueue.State.RETRY)) queue.retry(first.id, 25)
            var accepted = 0
            val fake = object : RecordingMailTransport {
                override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) { beforeData(); accepted++ }
                override fun close() = Unit
            }
            val profile = SmtpProfile("smtp.example.invalid", 587, false, "fixture", "fixture", "sender@example.invalid", 25)
            assertEquals(MailQueue.State.SENT, MailDispatcher(queue, fake).deliver(first.id, profile))
            assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
            assertEquals(MailQueue.State.SENT, MailDispatcher(queue, fake).deliver(first.id, profile)); assertEquals(1, accepted)
            val remaining = runMs - (android.os.SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0) Thread.sleep(remaining)
            scenario.onActivity { it.startService(Intent(it, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP)) }
            await { BackgroundCameraService.activeContinuousSession == null }
            ContinuousSessionStore.executor.submit {}.get()
            assertEquals("STOPPED", BackgroundCameraService.state.stopReason)
            val files = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!.listFiles().orEmpty()
                .filter { it.name.startsWith("NEXVARY_${id}_") && it.extension == "mp4" }.sortedBy { it.name }
            assertTrue("Expected multiple independent MP4 files", files.size >= if (long) 15 else 3)
            files.forEach { file ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val video = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString("mime")!!.startsWith("video/") }
                    extractor.selectTrack(video)
                    assertTrue(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    assertTrue(extractor.sampleTime >= 0)
                    var previous = -1L
                    while (extractor.sampleTime >= 0) {
                        assertTrue(extractor.sampleTime > previous); previous = extractor.sampleTime
                        if (!extractor.advance()) break
                    }
                    assertTrue(file.length() > 0)
                } finally { extractor.release() }
            }
            val jobs = queue.jobs().filter { it.id !in idsBefore }
            assertEquals(files.size, jobs.size)
            val before = jobs.map { it.id }.toSet()
            ContinuousSessionStore.executor.submit { ContinuousSessionStore(context).recover() }.get()
            assertEquals(before, queue.jobs().filter { it.id !in idsBefore }.map { it.id }.toSet())
            // No actual gap claim: raw segment PTS journals are exported by the CI workflow.
        } finally {
            context.startService(Intent(context, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
            scenario.close()
            if (id.isNotBlank()) {
                val dir = File(context.noBackupFilesDir, "continuous_sessions")
                val evidence = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "continuous-$id").apply { mkdirs() }
                dir.listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { it.copyTo(File(evidence, it.name), true) }
            }
        }
    }
    @Test fun durableQueueIsIdempotentAndRetainsUnsentFileAfterRestartAndUncertainData() {
        val root = File(context.cacheDir, "continuous-queue-${System.nanoTime()}").apply { mkdirs() }
        val private = File(root, "private").apply { mkdirs() }
        val movies = File(root, "movies").apply { mkdirs() }
        val app = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = private
            override fun getExternalFilesDir(type: String?) = movies
        }
        try {
            val source = File(movies, "clip.mp4").apply { writeBytes(ByteArray(8000) { it.toByte() }) }
            val session = java.util.UUID.randomUUID().toString()
            val key = SegmentTimeline.jobId(session, 1)
            val first = MailQueue(app).enqueue(source, "fixture@example.invalid", 25, key, true)
            val queue = MailQueue(app)
            assertEquals(first, queue.enqueue(source, "fixture@example.invalid", 25, key, true))
            assertEquals(1, queue.jobs().size)
            val transport = object : RecordingMailTransport {
                override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) { beforeData(); throw java.io.IOException("lost DATA acknowledgment") }
                override fun close() = Unit
            }
            val profile = SmtpProfile("smtp.example.invalid", 587, false, "fixture", "fixture", "sender@example.invalid", 25)
            assertEquals(MailQueue.State.UNCERTAIN, MailDispatcher(queue, transport).deliver(key, profile))
            assertNull(queue.claim(key)); assertTrue(source.isFile); assertTrue(queue.attachment(key).isFile)
            assertEquals(MailQueue.State.UNCERTAIN, MailQueue(app).get(key)?.state)
        } finally { root.deleteRecursively() }
    }
}
