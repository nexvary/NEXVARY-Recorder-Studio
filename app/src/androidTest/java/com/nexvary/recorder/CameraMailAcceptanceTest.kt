package com.nexvary.recorder

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.delivery.*
import com.nexvary.recorder.family.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Synthetic emulator fixtures only; no external SMTP account or real recipient. */
@RunWith(AndroidJUnit4::class)
class CameraMailAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val profile = SmtpProfile("smtp.example.invalid", 587, false, "fixture", "fixture-secret", "sender@example.invalid", 25)
    private var active = false
    private var previousLocales = ""
    @Before fun prepare() {
        Assume.assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish")); active = true
        instrumentation.runOnMainSync {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            previousLocales = manager.applicationLocales.toLanguageTags()
            manager.applicationLocales = android.os.LocaleList.forLanguageTags("ar")
        }
        GuardianGate.lock(); FamilyStore(context).update { it.remove("guardianPin") }
        SmtpSettings(context).clear()
        context.getSharedPreferences("camera_options", 0).edit().remove("recipient").commit()
    }
    @After fun clean() {
        if (!active) return
        SmtpSettings(context).clear(); FamilyStore(context).update { it.remove("guardianPin") }; GuardianGate.lock()
        instrumentation.runOnMainSync { context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(previousLocales) }
    }
    @Test fun encryptedRecipientDefaultsAndLegacyQueueRequireFreshApproval() {
        val root = File(context.cacheDir, "multi-mail-${System.nanoTime()}").apply { mkdirs() }
        val privateDir = File(root, "private").apply { mkdirs() }
        val movies = File(root, "movies").apply { mkdirs() }
        val app = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = privateDir
            override fun getExternalFilesDir(type: String?) = movies
        }
        try {
            val recipients = "one@example.invalid, two@example.invalid"
            val saved = profile.copy(cameraRecipients = recipients)
            SmtpSettings(app).save(saved); assertEquals(saved, SmtpSettings(app).load())
            val cipher = File(privateDir, "smtp-profile.aes").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(cipher.contains("one@example.invalid")); assertFalse(cipher.contains(profile.password))
            val source = File(movies, "saved.mp4").apply { writeBytes(ByteArray(1024)) }
            val queue = MailQueue(app); val job = queue.enqueue(source, recipients, 25)
            val record = File(privateDir, "mail_outbox/${job.id}.json")
            record.writeText(JSONObject(record.readText()).apply { remove("approved") }.toString())
            assertEquals(MailQueue.State.REVIEW, queue.get(job.id)?.state); assertNull(queue.claim(job.id))
            var sent = 0
            val fake = object : RecordingMailTransport {
                override fun send(profile: SmtpProfile, job: MailQueue.Job, file: File, beforeData: () -> Unit) { beforeData(); sent++ }
                override fun close() { }
            }
            val dispatcher = MailDispatcher(queue, fake)
            assertEquals(MailQueue.State.REVIEW, dispatcher.deliver(job.id, profile)); assertEquals(0, sent)
            val retry = queue.retry(job.id, 25, "two@example.invalid; three@example.invalid")!!
            assertTrue(retry.approved); assertEquals("two@example.invalid, three@example.invalid", retry.recipient)
            assertEquals(MailQueue.State.SENT, dispatcher.deliver(job.id, profile))
            assertEquals(MailQueue.State.SENT, dispatcher.deliver(job.id, profile)); assertEquals(1, sent)
            assertTrue(source.exists()); assertFalse(queue.attachment(job.id).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun multipleRecipientTlsEnvelopeKeepsAddressesOutOfMessageHeaders() {
        val assets = instrumentation.context.assets
        val key = assets.open("smtp-test-key.b64").use { it.readBytes() }
        val certificate = assets.open("smtp-test-cert.b64").use { it.readBytes() }
        val file = File(context.cacheDir, "tls-multi-${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(8001) { it.toByte() }) }
        try {
            for (direct in listOf(true, false)) LocalSmtpFixture(key, certificate, direct).use { server ->
                val job = MailQueue.Job(java.util.UUID.randomUUID().toString(), file.name,
                    "one@example.invalid,two@example.invalid,ONE@example.invalid", "", 1_790_000_000_000, MailQueue.State.QUEUED)
                SmtpSender("Local only", "Synthetic fixture", server.factory).send(
                    SmtpProfile("127.0.0.1", server.port, direct, "fixture-user", "fixture-password", "sender@example.invalid", 25), job, file) { }
                assertEquals(listOf("one@example.invalid", "two@example.invalid"), server.recipients.toList())
                assertEquals(1, server.dataCommands)
                val header = String(server.received, Charsets.US_ASCII).substringBefore("\r\n\r\n")
                assertFalse(header.contains("one@example.invalid")); assertFalse(header.contains("two@example.invalid"))
                val mime = jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()), java.io.ByteArrayInputStream(server.received))
                assertNull(mime.getHeader("Bcc"))
                assertArrayEquals(file.readBytes(), (mime.content as jakarta.mail.internet.MimeMultipart).getBodyPart(1).inputStream.readBytes())
            }
        } finally { file.delete() }
    }
    @Test fun reviewCancellationDoesNotStartRecordingOrQueueEmail() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        SmtpSettings(context).save(profile)
        val before = MailQueue(context).jobs().size
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
            onView(withId(R.id.cameraEmail)).perform(reveal(), click())
            onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText("one@example.invalid\ntwo@example.invalid;ONE@example.invalid"), closeSoftKeyboard())
            if (!BuildConfig.DEBUG) capture("camera-mail-release-ar")
            onView(withId(R.id.cameraStart)).perform(reveal(), click())
            awaitUi(withText(R.string.camera_mail_review_title))
            onView(withText(context.getString(R.string.camera_mail_review, "one@example.invalid\ntwo@example.invalid"))).check(matches(isDisplayed()))
            if (!BuildConfig.DEBUG) capture("camera-mail-review-release-ar")
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals(before, MailQueue(context).jobs().size)
            assertEquals("", SmtpSettings(context).load()!!.cameraRecipients)
            assertTrue(BackgroundCameraService.state.stage !in listOf(BackgroundCameraService.Companion.Stage.STARTING, BackgroundCameraService.Companion.Stage.RECORDING))
        }
    }
    @Test fun configuredParentPinGuardsMailReviewAndOutbox() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        SmtpSettings(context).save(profile); GuardianGate.create(context, "713926"); GuardianGate.lock()
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
            onView(withId(R.id.cameraEmail)).perform(reveal(), click())
            onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText("one@example.invalid,two@example.invalid"), closeSoftKeyboard())
            onView(withId(R.id.cameraStart)).perform(reveal(), click()); awaitUi(withId(R.id.fsGuardianPin))
            onView(withId(android.R.id.button2)).perform(click())
        }
        ActivityScenario.launch(MailOutboxActivity::class.java).use {
            awaitUi(withId(R.id.fsGuardianPin)); onView(withId(android.R.id.button2)).perform(click())
        }
        assertFalse(GuardianGate.unlocked())
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NEXVARYVisualEvidence"); put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try { checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
