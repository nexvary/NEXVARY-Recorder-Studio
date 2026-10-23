package com.nexvary.recorder

import android.Manifest
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.guardian.*
import com.nexvary.recorder.camera.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GuardianIntegrationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private var ownedEvent: String?=null
    @Before fun setup() {
        ownedEvent=null
        GuardianGate.lock()
        context.getSharedPreferences("guardian_mode",0).edit().clear().commit()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    @After fun cleanup() {
        EvidenceManager.stopOwned(context,null);runCatching { SafetyCheckManager(context).resolve(null,true) }
        val stopDeadline=SystemClock.elapsedRealtime()+10_000
        while(BackgroundCameraService.activeOutputPath!=null && SystemClock.elapsedRealtime()<stopDeadline) SystemClock.sleep(50)
        ownedEvent?.let { event ->
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(FamilyStore(context).snapshot().optJSONObject("evidenceActions")?.optString(event) in listOf("RECORDING","STARTING") && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(50)
            val rows=FamilyStore(context).snapshot().optJSONObject("evidence") ?: JSONObject()
            rows.keys().asSequence().map { rows.getJSONObject(it) }.filter { it.optString("eventId")==event }.forEach { manifest ->
                val name=manifest.getString("fileName")
                check(name==File(name).name)
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),name).delete()
            }
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),"${event}_evidence_manifest.json").delete()
        }
        GuardianGate.lock();context.getSharedPreferences("guardian_mode",0).edit().clear().commit()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    @Test fun audioLivesInsideMediaCenterAndSafetyHasHomeSlot() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.cardFamily)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.cardReplace)).perform(reveal(),click())
            awaitUi(withText(R.string.guardian_record_audio))
            onView(withText(R.string.guardian_record_audio)).perform(reveal(),click())
            awaitUi(withId(R.id.btnBack))
        }
    }
    @Test fun rapidOfflineSosPreservesOneEventAndEncryptedOutbox() {
        FamilyStore(context).update { it.put("config",JSONObject().put("autoShare",true)) }
        val first=SafetyCheckManager(context).sos(true)
        repeat(20) { assertEquals(first.id,SafetyCheckManager(context).sos(true).id) }
        val rows=CloudOutbox.events(FamilyStore(context).snapshot())
        assertEquals(1,rows.length());assertTrue(rows.has(first.id))
        assertEquals("PENDING",rows.getJSONObject(first.id).getString("status"))
        val bytes=File(context.noBackupFilesDir,"family-safety.aes").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains(first.id))
        assertTrue(FamilyStore.events(FamilyStore(context).snapshot()).length()>=20)
        assertTrue(SafetyCheckManager(context).resolve(first.id))
        assertEquals("CANCELLED",CloudOutbox.events(FamilyStore(context).snapshot()).getJSONObject(first.id).getString("status"))
    }
    @Test fun childHomeHidesCloudBillingAndParentExitNeedsPin() {
        GuardianGate.create(context,"123456");GuardianGate.lock()
        ActivityScenario.launch(ChildHomeActivity::class.java).use {
            onView(withText(R.string.fs_sos)).check(matches(isDisplayed()))
            onView(withText(R.string.guardian_cloud)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withText(R.string.fs_subscriptions)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withText(R.string.fs_parent_mode)).perform(reveal(),click())
            awaitUi(withId(R.id.fsGuardianPin));onView(withId(R.id.fsGuardianPin)).check(matches(isDisplayed()))
        }
    }
    @Test fun videoRolloverClosesAndHashesSegmentsBeforeSessionEnds() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val event=UUID.randomUUID().toString();ownedEvent=event
        ActivityScenario.launch(FamilyDashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ContextCompat.startForegroundService(activity,Intent(activity,BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                    .putExtra(BackgroundCameraService.EXTRA_EVIDENCE_EVENT,event).putExtra(BackgroundCameraService.EXTRA_QUALITY,"emergency").putExtra(BackgroundCameraService.EXTRA_AUDIO,true)
                    .putExtra(BackgroundCameraService.EXTRA_DURATION,65_000L))
            }
            val deadline=SystemClock.elapsedRealtime()+80_000
            var first: JSONObject?=null
            while(SystemClock.elapsedRealtime()<deadline && first==null) {
                val rows=FamilyStore(context).snapshot().optJSONObject("evidence")
                first=rows?.keys()?.asSequence()?.map { rows.getJSONObject(it) }?.firstOrNull { it.optString("eventId")==event }
                if(first==null) SystemClock.sleep(200)
            }
            assertNotNull("No closed segment before timeout",first)
            assertTrue("First segment did not finish before session end",first!!.optLong("durationMs")<60_000)
            assertTrue(EvidenceManager.verify(context,first))
            EvidenceManager.stopOwned(context,event)
            val stop=SystemClock.elapsedRealtime()+20_000
            while(BackgroundCameraService.activeOutputPath!=null && SystemClock.elapsedRealtime()<stop) SystemClock.sleep(100)
            val finalize=SystemClock.elapsedRealtime()+10_000
            while(FamilyStore(context).snapshot().optJSONObject("evidenceActions")?.optString(event)!="SAVED" && SystemClock.elapsedRealtime()<finalize) SystemClock.sleep(100)
            assertTrue(File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),"${event}_evidence_manifest.json").isFile)
        }
    }
}
