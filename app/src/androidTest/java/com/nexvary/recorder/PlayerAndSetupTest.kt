package com.nexvary.recorder

import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.media.*
import org.hamcrest.Matchers.allOf
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerAndSetupTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before @After fun clean() {
        GuardianGate.lock()
        context.getSharedPreferences("guardian_mode", 0).edit().clear().commit()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    private fun ready(scenario: ActivityScenario<InternalPlayerActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            var ready = false
            scenario.onActivity {
                val player = it.findViewById<PlayerView>(R.id.internalPlayer).player
                ready = player != null && player.playbackState in listOf(Player.STATE_READY, Player.STATE_ENDED) && player.duration > 0 && player.videoSize.width > 0
            }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Local sample did not decode into a ready video")
    }
    @Test fun localVideoDecodesAndRetainsPausedPositionAcrossRecreation() {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "qa-player.mp4")
        instrumentation.context.assets.open("qa-sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            ActivityScenario.launch<InternalPlayerActivity>(InternalPlayerActivity.intent(context, uri, file.name)).use { scenario ->
                ready(scenario)
                var target = 0L
                scenario.onActivity {
                    val player = it.findViewById<PlayerView>(R.id.internalPlayer).player!!
                    player.pause(); target = player.duration / 3; player.seekTo(target)
                }
                SystemClock.sleep(300)
                scenario.recreate(); ready(scenario)
                scenario.onActivity {
                    val player = it.findViewById<PlayerView>(R.id.internalPlayer).player!!
                    assertFalse(player.playWhenReady)
                    assertTrue("Seek position was lost", kotlin.math.abs(target - player.currentPosition) < 400)
                }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                scenario.onActivity { assertNull(it.findViewById<PlayerView>(R.id.internalPlayer).player) }
            }
        } finally { file.delete() }
    }
    @Test fun missingVideoShowsRecoverableErrorWithoutOpeningAnotherApp() {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "missing-qa.mp4"))
        ActivityScenario.launch<InternalPlayerActivity>(InternalPlayerActivity.intent(context, uri, "missing")).use {
            awaitUi(allOf(withId(R.id.playerStatus), withText(R.string.player_error)))
            onView(withId(R.id.playerChoose)).perform(reveal()).check(matches(isEnabled()))
        }
    }
    @Test fun libraryPlayUsesInternalPlayerWithoutAnExternalViewIntent() {
        val name = "QA-internal-${java.util.UUID.randomUUID()}.mp4"
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/NEXVARY Recorder/"); put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            instrumentation.context.assets.open("qa-sample.mp4").use { input -> checkNotNull(context.contentResolver.openOutputStream(uri)).use { input.copyTo(it) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            Intents.init()
            try {
                ActivityScenario.launch(RecordingsLibraryActivity::class.java).use {
                    awaitUi(withText(name))
                    onView(allOf(withText(R.string.library_play), withParent(hasDescendant(withText(name))))).perform(reveal(), click())
                    awaitUi(withId(R.id.internalPlayer))
                    Intents.intended(hasComponent(InternalPlayerActivity::class.java.name))
                    assertFalse(Intents.getIntents().any { intent -> intent.action == Intent.ACTION_VIEW })
                    pressBack()
                }
            } finally { Intents.release() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }
    @Test fun guidedProfilesPreserveHealthAndConsentAndResumeAfterRecreation() {
        FamilyStore(context).update {
            it.put("medical", JSONObject().put("allergies", "synthetic test allergy").put("notes", "retained"))
            it.put("config", JSONObject().put("shareMedical", false).put("location", false))
        }
        ActivityScenario.launch(GuidedSetupActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.setupSkip))
            onView(withText(R.string.fs_profile)).perform(reveal(), click())
            awaitUi(withId(R.id.fsSaveProfile))
            onView(withHint(R.string.fs_full_name)).perform(reveal(), replaceText("Phone user"), closeSoftKeyboard())
            onView(withId(R.id.fsSaveProfile)).perform(reveal(), click())
            awaitUi(allOf(withId(R.id.setupNext), isEnabled()))
            onView(withId(R.id.setupNext)).perform(reveal(), click())
            awaitUi(withId(R.id.setupGuardianName))
            onView(withId(R.id.setupGuardianName)).perform(reveal(), replaceText("Test guardian"), closeSoftKeyboard())
            onView(withId(R.id.setupGuardianPhone)).perform(reveal(), replaceText("01000000000"), closeSoftKeyboard())
            onView(withId(R.id.setupNext)).perform(reveal(), click())
            awaitUi(withId(R.id.setupChildName))
            onView(withId(R.id.setupChildName)).perform(reveal(), replaceText("Test child"), closeSoftKeyboard())
            onView(withId(R.id.setupChildAge)).perform(reveal(), replaceText("12"), closeSoftKeyboard())
            onView(withId(R.id.setupNext)).perform(reveal(), click())
            awaitUi(withId(R.id.setupCamera))
            scenario.recreate(); awaitUi(withId(R.id.setupCamera))
            val data = FamilyStore(context).snapshot()
            assertEquals(3, GuidedSetupProgress.data(data).getInt("step"))
            assertEquals("Test guardian", data.getJSONObject("medical").getString("guardian"))
            assertEquals("Test child", data.getJSONObject("medical").getString("name"))
            assertEquals("synthetic test allergy", data.getJSONObject("medical").getString("allergies"))
            assertEquals("retained", data.getJSONObject("medical").getString("notes"))
            assertFalse(FamilyStore.config(data).getBoolean("shareMedical"))
            assertFalse(FamilyStore.config(data).getBoolean("location"))
            assertFalse(data.has("cloud")); assertFalse(com.nexvary.recorder.guardian.ChildMode.enabled(context))
            onView(withId(R.id.setupNext)).perform(reveal(), click())
            awaitUi(withId(R.id.setupDone))
            onView(withId(R.id.setupDone)).perform(reveal(), click())
        }
        assertTrue(GuidedSetupProgress.data(FamilyStore(context).snapshot()).getBoolean("complete"))
    }
    @Test fun recordingRemainsAccessibleDuringIncompleteSetup() {
        ActivityScenario.launch(GuidedSetupActivity::class.java).use {
            awaitUi(withId(R.id.setupRecordNow))
            onView(withId(R.id.setupRecordNow)).perform(reveal(), click())
            awaitUi(withText(R.string.camera_title))
            assertFalse(GuidedSetupProgress.data(FamilyStore(context).snapshot()).optBoolean("complete"))
            assertNull(com.nexvary.recorder.camera.BackgroundCameraService.activeOutputPath)
            pressBack()
        }
    }
    @Test fun personalUseSkipsChildAccountAndRetainsSavedHealthData() {
        FamilyStore(context).update {
            it.put("medical", JSONObject().put("allergies", "retained"))
            GuidedSetupProgress.move(it, 1)
        }
        ActivityScenario.launch(GuidedSetupActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.setupSkip))
            onView(withId(R.id.setupSkip)).perform(reveal(), click())
            awaitUi(withId(R.id.setupCamera))
            scenario.recreate(); awaitUi(withId(R.id.setupCamera))
            val data = FamilyStore(context).snapshot()
            assertEquals(3, GuidedSetupProgress.data(data).getInt("step"))
            assertFalse(data.getJSONObject("childProfile").getBoolean("enabled"))
            assertEquals("retained", data.getJSONObject("medical").getString("allergies"))
            assertFalse(data.has("cloud"))
        }
    }
}
