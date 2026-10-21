package com.nexvary.recorder

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.delivery.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Synthetic recipients, loopback TLS and local-only evidence. No external alert is sent. */
@RunWith(AndroidJUnit4::class)
class FamilySafetyTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val profile=SmtpProfile("smtp.example.invalid",587,false,"fixture","fixture-only","sender@example.invalid",25)
    private fun contact(email: String="guardian@example.invalid")=JSONObject().put("id",UUID.randomUUID().toString()).put("name","Test guardian")
        .put("email",email).put("channel","EMAIL").put("role","PRIMARY")
    private fun isolated(block: (Context) -> Unit) {
        val folder=File(context.cacheDir,"family-${System.nanoTime()}").apply { mkdirs() }
        val app=object: ContextWrapper(context) { override fun getNoBackupFilesDir()=folder }
        try { block(app) } finally { folder.deleteRecursively() }
    }
    private fun clear() {
        runCatching { SafetyCheckManager(context).resolve(null,true) }
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach { root.remove(it) } }
        GuardianGate.lock()
    }
    @Before fun prepare() { clear() }
    @After fun cleanup() { clear() }
    @Test fun encryptedContactsAndMedicalProfileSurviveRecreationWithoutPlaintext()=isolated { app ->
        val contact=contact();TrustedContactsRepository(app).save(contact)
        FamilyStore(app).update { it.put("medical",JSONObject().put("allergies","Synthetic privacy marker")) }
        assertEquals(contact.getString("email"),TrustedContactsRepository(app).all().single().getString("email"))
        val bytes=File(app.noBackupFilesDir,"family-safety.aes").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains("guardian@example.invalid"));assertFalse(bytes.contains("Synthetic privacy marker"))
        assertEquals("Synthetic privacy marker",FamilyStore(app).snapshot().getJSONObject("medical").getString("allergies"))
    }
    @Test fun changedRecipientOrRevokedConsentCancelsQueuedAlertBeforeTransport()=isolated { app ->
        val guardian=contact();TrustedContactsRepository(app).save(guardian)
        val store=FamilyStore(app)
        fun enqueue(): String {
            store.update { root -> root.put("config",JSONObject().put("autoShare",true));EmergencyQueue.enqueue(root,AlertPayload.create(app,root,"SOS",UUID.randomUUID().toString())) }
            return EmergencyQueue(app).all().first { it.optString("status")=="QUEUED" }.getString("id")
        }
        val first=enqueue();guardian.put("email","changed@example.invalid");TrustedContactsRepository(app).save(guardian)
        assertNull(EmergencyQueue(app).claim(first))
        val second=enqueue();store.update { it.getJSONObject("config").put("autoShare",false) }
        assertNull(EmergencyQueue(app).claim(second))
        assertTrue(EmergencyQueue(app).all().all { it.optString("status")=="CANCELLED" })
    }
    @Test fun uncertainEmailDoesNotResendAndFailureDoesNotStopOtherGuardian()=isolated { app ->
        TrustedContactsRepository(app).save(contact());TrustedContactsRepository(app).save(contact("second@example.invalid"))
        FamilyStore(app).update { root -> root.put("config",JSONObject().put("autoShare",true));EmergencyQueue.enqueue(root,AlertPayload.create(app,root,"SOS",UUID.randomUUID().toString())) }
        var calls=0
        val transport=object: AlertTransport {
            override fun send(profile: SmtpProfile,id: String,recipient: String,payload: String,beforeData: () -> Unit) { calls++;beforeData();if(calls==1) throw java.net.SocketTimeoutException("Local simulated interruption") }
            override fun close() { }
        }
        val queue=EmergencyQueue(app);val jobs=queue.all()
        assertEquals("UNCERTAIN",queue.deliver(jobs[0].getString("id"),profile,transport))
        assertNull(queue.deliver(jobs[0].getString("id"),profile,transport))
        assertEquals("SENT",queue.deliver(jobs[1].getString("id"),profile,transport));assertEquals(2,calls)
    }
    @Test fun testAlertJsonUsesActualTlsRuntimeOnLoopback() {
        val assets=instrumentation.context.assets
        LocalSmtpFixture(assets.open("smtp-test-key.b64").use { it.readBytes() },assets.open("smtp-test-cert.b64").use { it.readBytes() },true).use { server ->
            val sender=SmtpSender("TEST ALERT — local fixture","No external recipient",server.factory)
            try {
                sender.sendAlert(SmtpProfile("127.0.0.1",server.port,true,"fixture","fixture-password","sender@example.invalid",25),UUID.randomUUID().toString(),"guardian@example.invalid",JSONObject().put("eventType","TEST_ALERT").toString()) { }
                val mime=jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()),server.received.inputStream())
                assertTrue(mime.subject.contains("TEST ALERT"))
                val parts=mime.content as jakarta.mail.internet.MimeMultipart
                assertEquals("TEST_ALERT",JSONObject(parts.getBodyPart(1).inputStream.bufferedReader().readText()).getString("eventType"))
            } finally { sender.close() }
        }
    }
    @Test fun safetyStateSurvivesActivityRecreationAndStaleCancelCannotStopNewSession() {
        val manager=SafetyCheckManager(context);val first=manager.start(15,60)
        ActivityScenario.launch(FamilyDashboardActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.fsSafe));scenario.recreate();awaitUi(withId(R.id.fsSafe))
            assertEquals(first.id,FamilyStore.state(FamilyStore(context).snapshot())!!.id)
            onView(withId(R.id.fsSafe)).perform(reveal(),click())
            await { FamilyStore.state(FamilyStore(context).snapshot())?.phase==SafetyPhase.RESOLVED }
        }
        val second=manager.start(30,60);assertFalse(manager.resolve(first.id,true))
        assertEquals(second.id,FamilyStore.state(FamilyStore(context).snapshot())!!.id)
        manager.resolve(second.id,true)
    }
    @Test fun automaticExpiryPersistsGraceAndQueuesNothingWithoutConsent() {
        val manager=SafetyCheckManager(context);val state=manager.start(15,20)
        fun expire()=FamilyStore(context).update { root -> root.getJSONObject("state").put("wall",0).put("elapsed",0) }
        expire();manager.evaluate(true)
        assertEquals(SafetyPhase.COUNTDOWN,FamilyStore.state(FamilyStore(context).snapshot())!!.phase)
        assertEquals(state.id,manager.sos().id)
        val deadline=FamilyStore.state(FamilyStore(context).snapshot())!!.deadline
        manager.sos();assertEquals(deadline,FamilyStore.state(FamilyStore(context).snapshot())!!.deadline)
        expire();manager.evaluate(true)
        assertEquals(SafetyPhase.SOS_ACTIVE,FamilyStore.state(FamilyStore(context).snapshot())!!.phase)
        assertTrue(EmergencyQueue(context).all().isEmpty());manager.resolve(state.id,true)
    }
    @Test fun guardianPinProtectsSettingsButDoesNotBlockSos() {
        GuardianGate.create(context,"123456");GuardianGate.lock()
        ActivityScenario.launch(TrustedContactsActivity::class.java).use {
            awaitUi(withId(R.id.fsGuardianPin));onView(withId(R.id.fsGuardianPin)).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
        }
        ActivityScenario.launch(FamilyDashboardActivity::class.java).use {
            awaitUi(withId(R.id.fsSos));onView(withId(R.id.fsSos)).perform(reveal(),click())
            await { FamilyStore.state(FamilyStore(context).snapshot())?.phase==SafetyPhase.COUNTDOWN }
            onView(withId(R.id.fsCancel)).perform(reveal(),click());onView(withId(android.R.id.button1)).perform(click())
            await { FamilyStore.state(FamilyStore(context).snapshot())?.phase==SafetyPhase.CANCELLED }
        }
        assertFalse(GuardianGate.verify(context,"654321"));assertTrue(GuardianGate.verify(context,"123456"))
    }
    @Test fun actualTenSecondAudioHasClosedFileDurationAndVerifiedHash() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val id=UUID.randomUUID().toString();var manifest: JSONObject?=null
        try {
            ActivityScenario.launch(FamilyDashboardActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.fsSafe))
                scenario.onActivity { ContextCompat.startForegroundService(it,Intent(it,AudioEvidenceService::class.java).putExtra("event",id).putExtra("duration",10_000)) }
                await(30_000) { manifest=findManifest(id);manifest!=null }
                assertTrue(manifest!!.getLong("durationMs") in 8_000..14_000)
                assertTrue(EvidenceManager.verify(context,manifest!!));assertTrue(EmergencyQueue(context).all().isEmpty())
            }
        } finally { context.stopService(Intent(context,AudioEvidenceService::class.java));manifest?.let { File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),it.getString("fileName")).delete() } }
    }
    @Test fun actualTenSecondEmergencyVideoHasManifestAndNoExternalUpload() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val id=UUID.randomUUID().toString();var manifest: JSONObject?=null
        try {
            ActivityScenario.launch(FamilyDashboardActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.fsSafe))
                scenario.onActivity { ContextCompat.startForegroundService(it,Intent(it,BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                    .putExtra(BackgroundCameraService.EXTRA_EVIDENCE_EVENT,id).putExtra(BackgroundCameraService.EXTRA_DURATION,10_000L).putExtra(BackgroundCameraService.EXTRA_QUALITY,"emergency")) }
                await(35_000) { manifest=findManifest(id);manifest!=null }
                assertTrue(manifest!!.getLong("durationMs") in 8_000..14_000)
                assertTrue(EvidenceManager.verify(context,manifest!!));assertTrue(EmergencyQueue(context).all().isEmpty())
                assertTrue("Emergency size budget",manifest!!.getLong("fileSize")<2_500_000)
            }
        } finally { context.stopService(Intent(context,BackgroundCameraService::class.java));manifest?.let { File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),it.getString("fileName")).delete() } }
    }
    private fun findManifest(id: String): JSONObject? = FamilyStore(context).snapshot().optJSONObject("evidence")?.let { rows -> rows.keys().asSequence().map { rows.getJSONObject(it) }.firstOrNull { it.optString("eventId")==id } }
    private fun await(timeout: Long=10_000,predicate: () -> Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) { if(predicate()) return;SystemClock.sleep(50) }
        throw AssertionError("Safety condition did not complete within ${timeout}ms")
    }
}
