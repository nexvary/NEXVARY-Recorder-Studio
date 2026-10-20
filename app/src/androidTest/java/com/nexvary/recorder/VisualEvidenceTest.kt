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

/** Captures only actual windows from this installed APK. Never sends messages or starts recording. */
@RunWith(AndroidJUnit4::class)
class VisualEvidenceTest {
    @Test fun captureApprovedArabicScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                instrumentation.targetContext.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags("ar")
            } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
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
                // Espresso visibility can precede the SurfaceFlinger first presented frame.
                val frameCommitted = java.util.concurrent.CountDownLatch(1)
                it.onActivity { activity ->
                    val decor = activity.window.decorView
                    decor.viewTreeObserver.registerFrameCommitCallback { frameCommitted.countDown() }
                    decor.invalidate()
                }
                check(frameCommitted.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "No committed app frame" }
                val focus = instrumentation.uiAutomation.executeShellCommand("dumpsys window windows").use { fd ->
                    java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
                }
                check(!focus.contains("Application Error") && !focus.contains("Application Not Responding")) { "System error covers the app" }
                var captured: Bitmap? = null
                repeat(10) {
                    if (captured == null) {
                        instrumentation.uiAutomation.waitForIdle(300, 5_000)
                        val candidate = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        val readable = candidate.copy(Bitmap.Config.ARGB_8888, false)
                        var dark = 0
                        var sampled = 0
                        for (y in 0 until readable.height step 24) for (x in 0 until readable.width step 24) {
                            val color = readable.getPixel(x, y)
                            if (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color) < 300) dark++
                            sampled++
                        }
                        readable.recycle()
                        if (dark > sampled / 2) captured = candidate else candidate.recycle()
                    }
                }
                val screenshot = checkNotNull(captured) { "Starting window or blank frame captured for $name" }
                val resolver = context.contentResolver
                val uri = checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NEXVARYVisualEvidence")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }))
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                resolver.update(uri, android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                screenshot.recycle()
            }
        }
    }
}
