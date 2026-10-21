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
        com.nexvary.recorder.family.RescueCardsStore(context).clear()
        val pages = listOf(
            Triple(MainActivity::class.java, R.id.cardScreen, "dashboard-ar"),
            Triple(com.nexvary.recorder.family.FamilyDashboardActivity::class.java,R.id.fsSafe,"family-dashboard-ar"),
            Triple(com.nexvary.recorder.family.PrivacyCenterActivity::class.java,R.id.btnBack,"privacy-center-ar"),
            Triple(com.nexvary.recorder.safety.QuickRescueActivity::class.java, R.id.btnAddContact, "sos-ar"),
            Triple(com.nexvary.recorder.camera.BackgroundCameraActivity::class.java, R.id.btnBack, "camera-ar"),
            Triple(SettingsActivity::class.java, R.id.btnBack, "settings-ar"),
            Triple(com.nexvary.recorder.delivery.MailOutboxActivity::class.java, R.id.btnBack, "outbox-ar"),
            Triple(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java, R.id.btnBack, "library-ar")
        )
        com.nexvary.recorder.ui.ThemeManager.select(context, 0)
        val sample = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "QA-sample.mp4")
        instrumentation.context.assets.open("qa-sample.mp4").use { input -> sample.outputStream().use { input.copyTo(it) } }
        try {
        for ((page, visibleId, name) in pages) {
            ActivityScenario.launch(page).use {
                onView(withId(visibleId)).check(matches(isDisplayed()))
                capture(it, name)
                if (page == com.nexvary.recorder.camera.BackgroundCameraActivity::class.java) {
                    onView(withId(R.id.cameraQualityGroup)).perform(reveal()).check(matches(isDisplayed()))
                    capture(it, "camera-quality-ar")
                    onView(withId(R.id.cameraStart)).perform(reveal()).check(matches(isDisplayed()))
                    capture(it, "camera-delivery-ar")
                }
                if(page == com.nexvary.recorder.family.FamilyDashboardActivity::class.java) {
                    com.nexvary.recorder.family.SafetyCheckManager(context).sos(false)
                    val until=android.os.SystemClock.uptimeMillis()+5000
                    while(android.os.SystemClock.uptimeMillis()<until && !com.nexvary.recorder.family.FamilyStore(context).snapshot().has("state")) android.os.SystemClock.sleep(50)
                    androidx.test.espresso.Espresso.onIdle()
                    capture(it,"family-sos-countdown-ar")
                    com.nexvary.recorder.family.SafetyCheckManager(context).resolve(null,true)
                }
                if (page == MainActivity::class.java) {
                    com.nexvary.recorder.ui.ThemeManager.select(context, 3)
                    it.recreate()
                    onView(withId(R.id.cardScreen)).check(matches(isDisplayed()))
                    capture(it, "dashboard-purple-ar")
                    com.nexvary.recorder.ui.ThemeManager.select(context, 0)
                }
                if (page == com.nexvary.recorder.safety.QuickRescueActivity::class.java) {
                    onView(withId(R.id.btnAddContact)).perform(reveal(), androidx.test.espresso.action.ViewActions.click())
                    onView(withId(R.id.rescueMessage)).check(matches(isDisplayed()))
                    capture(it, "sos-editor-ar")
                    onView(withId(android.R.id.button2)).perform(androidx.test.espresso.action.ViewActions.click())
                }

            }
        }
        // A synthetic contact is shown for border review; no send or location action runs.
        val contact = org.json.JSONObject().put("name", "جهة اختبار")
            .put("phone", "+201000000000").put("message", "رسالة تجريبية للمراجعة فقط")
            .put("location", false).put("image", "")
        context.getSharedPreferences("sos_cards", 0).edit()
            .putString("cards", org.json.JSONArray().put(contact).toString()).commit()
        ActivityScenario.launch(com.nexvary.recorder.safety.QuickRescueActivity::class.java).use {
            onView(withText(R.string.rescue_delete)).perform(reveal()).check(matches(isDisplayed()))
            capture(it, "sos-contact-ar")
        }
        } finally { com.nexvary.recorder.family.RescueCardsStore(context).clear(); sample.delete(); com.nexvary.recorder.ui.ThemeManager.select(context, 0) }
    }

    private fun capture(scenario: ActivityScenario<out android.app.Activity>, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
                instrumentation.waitForIdleSync()
                // Espresso visibility can precede the SurfaceFlinger first presented frame.
                val frameCommitted = java.util.concurrent.CountDownLatch(1)
                scenario.onActivity { activity ->
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
                        val readable = checkNotNull(candidate.copy(Bitmap.Config.ARGB_8888, false))
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
