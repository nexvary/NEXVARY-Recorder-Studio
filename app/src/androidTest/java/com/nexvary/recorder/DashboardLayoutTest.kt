package com.nexvary.recorder

import android.app.LocaleManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.ui.ThemeManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Regression for the owner's 20:9 phone screenshot, not an OPPO hardware test. */
@RunWith(AndroidJUnit4::class)
class DashboardLayoutTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun profile(scale: Float, body: () -> Unit) {
        assumeTrue("Display overrides are emulator-only", Build.HARDWARE in listOf("ranchu", "goldfish"))
        val size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val density = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        val font = Settings.System.getFloat(context.contentResolver, "font_scale", 1f)
        val originalDensity = context.resources.configuration.densityDpi
        val originalWidth = context.resources.configuration.screenWidthDp
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        val theme = ThemeManager.currentIndex(context)
        try {
            context.getSharedPreferences("guardian_mode", 0).edit().clear().commit()
            ThemeManager.select(context, 0)
            shell("wm size 720x1600"); shell("wm density 320")
            shell("settings put system font_scale $scale")
            awaitProfile(scale, 320, 360)
            body()
        } finally {
            shell("wm size ${size ?: "reset"}"); shell("wm density ${density ?: "reset"}")
            shell("settings put system font_scale $font")
            awaitProfile(font, originalDensity, originalWidth)
            instrumentation.runOnMainSync {
                context.getSystemService(LocaleManager::class.java).applicationLocales = locales
            }
            ThemeManager.select(context, theme)
        }
    }
    private fun awaitProfile(font: Float, density: Int, width: Int) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val config = context.resources.configuration
            if (kotlin.math.abs(config.fontScale - font) < 0.05f && config.densityDpi == density && config.screenWidthDp == width) return
            SystemClock.sleep(50)
        }
        fail("Emulator display configuration did not settle")
    }
    private fun language(tag: String) = instrumentation.runOnMainSync {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun phoneSizedDashboardKeepsAllNineCardsAboveNavigation() = profile(1f) {
        val measurements = JSONArray()
        for (locale in listOf("ar", "en")) {
            language(locale)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.cardAbout))
                scenario.onActivity { activity ->
                    assertEquals(360, activity.resources.configuration.screenWidthDp)
                    assertEquals(1f, activity.resources.configuration.fontScale, 0.05f)
                    val nav = Rect(); activity.findViewById<View>(R.id.bottomNav).getGlobalVisibleRect(nav)
                    val rows = JSONArray()
                    for (id in listOf(R.id.cardCamera, R.id.cardScreen, R.id.cardReplace,
                        R.id.cardRescue, R.id.cardLive, R.id.cardFamily,
                        R.id.cardLibrary, R.id.cardSettings, R.id.cardAbout)) {
                        val card = activity.findViewById<View>(id); val rect = Rect()
                        assertTrue("Card is not visible: $id", card.getGlobalVisibleRect(rect))
                        assertEquals("Card clipped vertically: $id", card.height, rect.height())
                        assertEquals("Card clipped horizontally: $id", card.width, rect.width())
                        assertTrue("Card overlaps navigation: $id", rect.bottom <= nav.top)
                        for (label in descendants(card).filterIsInstance<TextView>()) {
                            assertNotNull(label.layout)
                            assertTrue("Card text is clipped", label.layout.getLineBottom(label.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                        }
                        rows.put(JSONObject().put("id", activity.resources.getResourceEntryName(id)).put("bottom", rect.bottom))
                    }
                    assertEquals("Dashboard should fit at initial scroll", 0, activity.findViewById<ScrollView>(R.id.dashboardScroll).scrollY)
                    val entry = activity.findViewById<ViewGroup>(R.id.setupEntry).getChildAt(0)
                    val dp = entry.height / activity.resources.displayMetrics.density
                    assertTrue("Setup entry must remain tappable and compact: $dp", dp in 48f..64f)
                    val title = descendants(entry).filterIsInstance<TextView>().single()
                    assertTrue("Setup label has insufficient dark-card contrast", ColorUtils.calculateContrast(title.currentTextColor, 0xFF242424.toInt()) >= 4.5)
                    measurements.put(JSONObject().put("locale", locale).put("setupHeightDp", dp.toDouble()).put("navigationTop", nav.top).put("cards", rows))
                }
                capture("dashboard-compact-$locale")
            }
        }
        save("dashboard-compact-measurements.json", "application/json", "Download/NEXVARYTestEvidence") { it.write(measurements.toString(2).toByteArray()) }
    }

    @Test fun largeTextKeepsTheLastRowReachableWithoutClippingLabels() = profile(1.5f) {
        language("ar")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.cardCamera))
            onView(withId(R.id.cardAbout)).perform(reveal()).check(matches(isCompletelyDisplayed()))
            scenario.onActivity { activity ->
                assertEquals(1.5f, activity.resources.configuration.fontScale, 0.05f)
                val about = activity.findViewById<View>(R.id.cardAbout)
                for (label in descendants(about).filterIsInstance<TextView>()) {
                    assertTrue("Large-text label clipped", label.layout.getLineBottom(label.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                }
            }
            capture("dashboard-compact-large-text-ar")
        }
    }
    private fun save(name: String, mime: String, path: String, writer: (java.io.OutputStream) -> Unit) {
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(if (mime == "image/png") MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, path); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        checkNotNull(resolver.openOutputStream(uri)).use(writer)
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync(); SystemClock.sleep(250)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { save("$name.png", "image/png", "Pictures/NEXVARYVisualEvidence") { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { image.recycle() }
    }
}
