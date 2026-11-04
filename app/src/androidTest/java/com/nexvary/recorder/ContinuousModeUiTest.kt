package com.nexvary.recorder

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.camera.BackgroundCameraService
import com.nexvary.recorder.camera.ContinuousSessionStore
import org.hamcrest.Matchers.anything
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContinuousModeUiTest {
    @Test fun arabicContinuousControlsSelectDurationDisableTimerAndSurviveRecreation() {
        Assume.assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("camera_options", 0)
        val previous = preferences.all.filterKeys { it in listOf("continuous", "segment_seconds", "retry_network") }
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        val previousLocales = localeManager.applicationLocales
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        fun awaitCapture(predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
            while (!predicate() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            check(predicate()) { "UI-started continuous camera did not reach expected state" }
        }
        try {
            preferences.edit().putBoolean("continuous", false).putInt("segment_seconds", 60).commit()
            instrumentation.runOnMainSync { localeManager.applicationLocales = android.os.LocaleList.forLanguageTags("ar") }
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
                scenario.onActivity { localeManager.applicationLocales = android.os.LocaleList.forLanguageTags("ar") }
                scenario.recreate(); awaitUi(withId(R.id.continuousEnable))
                scenario.onActivity { assertEquals("ar", it.resources.configuration.locales[0].language) }
                onView(withId(R.id.continuousEnable)).perform(reveal(), click())
                onView(withId(R.id.continuousEnable)).check(matches(isChecked()))
                onView(withId(R.id.cameraTimer)).check(matches(isNotEnabled()))
                scenario.onActivity { activity ->
                    assertEquals(1, activity.findViewById<android.widget.Spinner>(R.id.continuousDuration).selectedItemPosition)
                }
                onView(withId(R.id.continuousDuration)).perform(reveal(), click())
                onData(anything()).atPosition(0).perform(click())
                onView(withId(R.id.continuousDuration)).check(matches(withSpinnerText(R.string.continuous_30)))
                scenario.recreate(); awaitUi(withId(R.id.continuousEnable))
                onView(withId(R.id.continuousEnable)).perform(reveal()).check(matches(isChecked()))
                onView(withId(R.id.continuousDuration)).check(matches(withSpinnerText(R.string.continuous_30)))
                onView(withId(R.id.cameraTimer)).check(matches(isNotEnabled()))
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val output = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "continuous-mode-ar.png")
                output.outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                screenshot.recycle()
                onView(withId(R.id.cameraStart)).perform(reveal(), click())
                awaitCapture { BackgroundCameraService.activeContinuousSession != null && BackgroundCameraService.state.stage == BackgroundCameraService.Companion.Stage.RECORDING }
                Thread.sleep(3000)
                onView(withId(R.id.cameraStop)).perform(reveal(), click())
                awaitCapture { BackgroundCameraService.activeContinuousSession == null }
                ContinuousSessionStore.executor.submit {}.get()
                assertEquals("STOPPED", BackgroundCameraService.state.stopReason)
                assertEquals(1, BackgroundCameraService.state.segmentNumber)
            }
        } finally {
            if (BackgroundCameraService.activeContinuousSession != null) runCatching {
                context.startService(Intent(context, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
            }
            val editor = preferences.edit().remove("continuous").remove("segment_seconds").remove("retry_network")
            previous.forEach { (key, value) -> when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
            } }
            editor.commit()
            instrumentation.runOnMainSync { localeManager.applicationLocales = previousLocales }
        }
    }
}
