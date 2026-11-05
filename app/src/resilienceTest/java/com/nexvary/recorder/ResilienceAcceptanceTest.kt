package com.nexvary.recorder

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.delivery.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import javax.net.ssl.SSLContext

/** Separate, emulator-only acceptance APK. No live mailbox, production trust change or UTP uninstall. */
@RunWith(AndroidJUnit4::class)
class ResilienceAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val movies get() = requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
    private fun guard() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Dedicated emulator required" }
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    private fun waitFor(timeout: Long = 20_000, message: String, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!ready() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue(message, ready())
    }
    private fun start(activity: BackgroundCameraActivity) {
        activity.startForegroundService(Intent(activity, BackgroundCameraService::class.java)
            .setAction(BackgroundCameraService.ACTION_START)
            .putExtra(BackgroundCameraService.EXTRA_CONTINUOUS, true)
            .putExtra(BackgroundCameraService.EXTRA_SEGMENT_SECONDS, 60)
            .putExtra(BackgroundCameraService.EXTRA_RECIPIENT, "fixture@example.invalid"))
    }
    private fun stop() {
        if (BackgroundCameraService.activeContinuousSession != null)
            app.startService(Intent(app, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
    }
    private fun jobs(id: String) = MailQueue(app).jobs().filter { it.name.startsWith("NEXVARY_${id}_") }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun publish(name: String, value: JSONObject) = ContinuousTestEvidence.write(app, name, "application/json", value.toString(2).toByteArray())

    @Test fun fiveMinuteNetworkOutageResumesRealMailWorkerOverPinnedLocalTls() {
        guard()
        val connection = app.getSystemService(ConnectivityManager::class.java)
        val previousProfile = SmtpSettings(app).load()
        val previousTls = SSLContext.getDefault()
        val assets = instrumentation.context.assets
        val key = assets.open("smtp-test-key.b64").use { it.readBytes() }
        val cert = assets.open("smtp-test-cert.b64").use { it.readBytes() }
        val scenario = ActivityScenario.launch(BackgroundCameraActivity::class.java)
        try {
            LocalSmtpFixture(key, cert, true, acceptTimeoutMs = 360_000, maxConnections = 16).use { server ->
                // Trust ONLY the public fixture certificate for this test process. Hostname verification stays on.
                SSLContext.setDefault(server.ssl)
                SmtpSettings(app).save(SmtpProfile("127.0.0.1", server.port, true, "fixture-user", "fixture-password", "sender@example.invalid", 25))
                shell("svc wifi disable"); shell("svc data disable")
                waitFor(message = "Default network must actually disconnect") { connection.activeNetwork == null }
                val offlineStart = SystemClock.elapsedRealtime()
                scenario.onActivity { start(it) }
                waitFor(message = "Camera must start while offline") {
                    BackgroundCameraService.activeContinuousSession != null && BackgroundCameraService.state.stage == BackgroundCameraService.Companion.Stage.RECORDING
                }
                val id = requireNotNull(BackgroundCameraService.activeContinuousSession)
                var disconnectedChecks = 0
                while (SystemClock.elapsedRealtime() - offlineStart < 300_000) {
                    assertNull("Internet must remain disconnected for the whole window", connection.activeNetwork)
                    assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
                    jobs(id).forEach { job ->
                        assertEquals(MailQueue.State.QUEUED, job.state); assertEquals(0, job.attempts)
                        assertTrue(File(job.original).isFile); assertTrue(MailQueue(app).attachment(job.id).isFile)
                    }
                    disconnectedChecks++; Thread.sleep(1000)
                }
                val offlineMs = SystemClock.elapsedRealtime() - offlineStart
                val waiting = jobs(id).map { it.id }
                assertTrue("Several clips must accumulate during the outage", waiting.size >= 4)
                assertEquals(0, server.dataCommands)
                shell("svc wifi enable")
                waitFor(30_000, "Default network must return") { connection.activeNetwork != null }
                waitFor(120_000, "Real production MailWorker must submit all offline clips") {
                    waiting.all { MailQueue(app).get(it)?.state == MailQueue.State.SENT }
                }
                assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
                Thread.sleep(3000); scenario.onActivity { stop() }
                waitFor(message = "User stop must finish camera") { BackgroundCameraService.activeContinuousSession == null }
                ContinuousSessionStore.executor.submit {}.get()
                waitFor(120_000, "Final clip must also be submitted automatically") { jobs(id).isNotEmpty() && jobs(id).all { it.state == MailQueue.State.SENT } }
                val completed = jobs(id)
                assertEquals(completed.size, server.dataCommands); assertEquals(completed.size, server.messages.size)
                val byMessageId = server.messages.map { bytes ->
                    jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()), bytes.inputStream())
                }.associateBy { it.messageID }
                assertEquals(completed.size, byMessageId.size)
                completed.forEach { job ->
                    val mime = checkNotNull(byMessageId["<${job.id}@recordings.nexvary.invalid>"])
                    val attachment = (mime.content as jakarta.mail.internet.MimeMultipart).getBodyPart(1).inputStream.use { it.readBytes() }
                    assertArrayEquals(File(job.original).readBytes(), attachment)
                    assertFalse(MailQueue(app).attachment(job.id).exists())
                }
                publish("resilience-network.json", JSONObject().put("emulator", true).put("offlineMs", offlineMs)
                    .put("disconnectedChecks", disconnectedChecks).put("queuedBeforeRecovery", waiting.size)
                    .put("clips", completed.size).put("smtpDataCommands", server.dataCommands).put("uniqueMessageIds", byMessageId.size)
                    .put("productionMailWorker", true).put("transport", "authenticated TLS loopback; pinned fixture certificate")
                    .put("externalInboxVerified", false).put("session", id))
            }
        } finally {
            stop(); shell("svc wifi enable"); shell("svc data enable")
            SSLContext.setDefault(previousTls)
            if (previousProfile == null) SmtpSettings(app).clear() else SmtpSettings(app).save(previousProfile)
            scenario.close()
        }
    }

    @Test fun prepareLiveSessionForHostSigkill() {
        guard(); SmtpSettings(app).clear()
        val scenario = ActivityScenario.launch(BackgroundCameraActivity::class.java)
        scenario.onActivity { start(it) }
        waitFor(85_000, "First clip must finalize while second records") { BackgroundCameraService.state.segmentNumber >= 2 }
        val id = requireNotNull(BackgroundCameraService.activeContinuousSession)
        ContinuousSessionStore.executor.submit {}.get()
        val first = File(movies, "NEXVARY_${id}_000001.mp4")
        assertTrue(first.length() > 0)
        Thread.sleep(5000)
        val value = JSONObject().put("session", id).put("pid", android.os.Process.myPid())
            .put("original", first.name).put("sha256", hash(first)).put("job", SegmentTimeline.jobId(id, 1))
            .put("pending", File(requireNotNull(BackgroundCameraService.activeOutputPath)).name)
        assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
        assertTrue(File(requireNotNull(BackgroundCameraService.activeOutputPath)).length() > 0)
        publish("resilience-kill-ready.json", value)
        // Host kills this process while the recorder is live. Returning normally is a failure.
        Thread.sleep(90_000)
        scenario.close(); stop(); fail("Host did not SIGKILL the live application")
    }

    @Test fun verifyRecoveryAfterHostSigkill() {
        guard()
        val cursor = checkNotNull(app.contentResolver.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.MediaColumns._ID), android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?",
            arrayOf("resilience-kill-ready.json"), null))
        val uri = cursor.use { check(it.moveToFirst()); android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.getLong(0)) }
        val seed = JSONObject(checkNotNull(app.contentResolver.openInputStream(uri)).use { it.readBytes().toString(Charsets.UTF_8) })
        val id = seed.getString("session")
        assertNotEquals(seed.getInt("pid"), android.os.Process.myPid())
        ContinuousSessionStore.executor.submit { ContinuousSessionStore(app).recover() }.get()
        val dir = File(app.noBackupFilesDir, "continuous_sessions")
        val session = JSONObject(File(dir, "$id.json").readText())
        assertEquals("INTERRUPTED", session.getString("state"))
        assertEquals(seed.getString("sha256"), hash(File(movies, seed.getString("original"))))
        assertNotNull(MailQueue(app).get(seed.getString("job")))
        val pending = File(movies, seed.getString("pending"))
        assertTrue("Unfinished tail must remain or be salvaged", pending.exists() || File(movies, pending.nameWithoutExtension + ".mp4").exists())
        val before = jobs(id).map { it.id }.toSet()
        repeat(3) { ContinuousSessionStore.executor.submit { ContinuousSessionStore(app).recover() }.get() }
        assertEquals(before, jobs(id).map { it.id }.toSet())
        assertNull(BackgroundCameraService.activeContinuousSession)
        assertTrue(BackgroundCameraService.state.stage !in listOf(BackgroundCameraService.Companion.Stage.STARTING, BackgroundCameraService.Companion.Stage.RECORDING))
        publish("resilience-process-recovery.json", JSONObject().put("emulator", true).put("session", id)
            .put("oldPid", seed.getInt("pid")).put("newPid", android.os.Process.myPid()).put("state", session.getString("state"))
            .put("completedClipSha256", seed.getString("sha256")).put("tailRetainedOrSalvaged", true)
            .put("queueIdentityStable", true).put("cameraAutomaticallyRestarted", false))
    }
}
