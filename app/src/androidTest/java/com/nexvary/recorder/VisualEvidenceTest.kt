package com.nexvary.recorder

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures only actual windows from this installed APK. Never sends messages or starts recording. */
@RunWith(AndroidJUnit4::class)
class VisualEvidenceTest {
    @Test fun captureApprovedArabicScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
        val context = instrumentation.targetContext
        context.getSharedPreferences("sos_cards", 0).edit().clear().commit()
        val pages = listOf(
            Triple(MainActivity::class.java, R.id.cardScreen, "dashboard-ar"),
            Triple(com.nexvary.recorder.safety.QuickRescueActivity::class.java, R.id.btnAddContact, "sos-ar"),
            Triple(com.nexvary.recorder.camera.BackgroundCameraActivity::class.java, R.id.btnBack, "camera-ar"),
            Triple(SettingsActivity::class.java, R.id.btnBack, "settings-ar"),
            Triple(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java, R.id.btnBack, "library-ar")
        )
        for ((page, visibleId, name) in pages) {
            ActivityScenario.launch(page).use {
                onView(withId(visibleId)).check(matches(isDisplayed()))
                instrumentation.waitForIdleSync()
                val focus = instrumentation.uiAutomation.executeShellCommand("dumpsys window windows").use { fd ->
                    java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
                }
                check(!focus.contains("Application Error") && !focus.contains("Application Not Responding")) { "System error covers the app" }
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(context.getExternalFilesDir(null), "visual-evidence/$name.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { output -> check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                screenshot.recycle()
            }
        }
    }
}
