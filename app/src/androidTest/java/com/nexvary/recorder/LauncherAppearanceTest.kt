package com.nexvary.recorder

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.family.*
import com.nexvary.recorder.privacy.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Dedicated emulator tests; changes only this application's launcher components. */
@RunWith(AndroidJUnit4::class)
class LauncherAppearanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val appearance get() = LauncherAppearance(app)
    private var previousPin: org.json.JSONObject? = null
    private fun waitFor(message: String, ready: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 20_000
        while (!ready() && SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue(message, ready())
    }
    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Dedicated emulator required" }
        previousPin = FamilyStore(app).snapshot().optJSONObject("guardianPin")
        FamilyStore(app).update { it.remove("guardianPin") }; GuardianGate.lock()
        appearance.select(LauncherAppearance.Style.ORIGINAL).getOrThrow()
    }
    @After fun restore() {
        appearance.select(LauncherAppearance.Style.ORIGINAL).getOrThrow()
        FamilyStore(app).update { root -> root.remove("guardianPin"); previousPin?.let { root.put("guardianPin", it) } }
        GuardianGate.lock()
    }
    @Test fun aliasesHaveIndependentLabelsPersistAndAlwaysKeepOneLaunchableEntry() {
        val applicationLabel = app.applicationInfo.loadLabel(app.packageManager).toString()
        for (style in LauncherAppearance.Style.entries) {
            appearance.select(style).getOrThrow()
            assertEquals(style, LauncherAppearance(app).current())
            assertEquals(listOf(style), appearance.enabled())
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName)
            waitFor("Exactly one real launcher entry must resolve") {
                val entries = app.packageManager.queryIntentActivities(query, 0)
                entries.size == 1 && entries.single().activityInfo.name == appearance.component(style).className
            }
            val info = app.packageManager.getActivityInfo(appearance.component(style), 0)
            assertEquals(MainActivity::class.java.name, info.targetActivity)
            val expected = when (style) {
                LauncherAppearance.Style.ORIGINAL -> R.string.app_name
                LauncherAppearance.Style.CLOCK -> R.string.launcher_clock
                LauncherAppearance.Style.CALENDAR -> R.string.launcher_calendar
            }
            assertEquals(app.getString(expected), info.loadLabel(app.packageManager).toString())
            assertNotNull(info.loadIcon(app.packageManager))
            assertEquals(applicationLabel, app.applicationInfo.loadLabel(app.packageManager).toString())
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(appearance.component(style)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(launch).use { scenario -> scenario.onActivity { assertEquals(app.packageName, it.packageName) } }
        }
    }
    @Test fun concurrentControllersCannotLeaveAllAliasesDisabled() {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            repeat(4) {
                val barrier = java.util.concurrent.CyclicBarrier(3)
                val work = LauncherAppearance.Style.entries.map { style -> java.util.concurrent.Callable {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    LauncherAppearance(app).select(style).getOrThrow()
                } }
                pool.invokeAll(work).forEach { it.get() }
                assertEquals(1, appearance.enabled().size)
            }
        } finally { pool.shutdownNow() }
    }
    @Test fun realSelectionCancellationConfirmationAndRotationPreserveTheChoice() {
        val localeManager = if (Build.VERSION.SDK_INT >= 33) app.getSystemService(android.app.LocaleManager::class.java) else null
        val previousLocales = if (Build.VERSION.SDK_INT >= 33) localeManager!!.applicationLocales else null
        if (Build.VERSION.SDK_INT >= 33) instrumentation.runOnMainSync { localeManager!!.applicationLocales = android.os.LocaleList.forLanguageTags("ar") }
        try {
        ActivityScenario.launch(LauncherAppearanceActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.launcherClock))
            onView(withId(R.id.launcherClock)).perform(reveal(), click())
            onView(withId(R.id.launcherApply)).perform(reveal(), click())
            awaitUi(withId(android.R.id.button2)); onView(withId(android.R.id.button2)).perform(click())
            assertEquals(LauncherAppearance.Style.ORIGINAL, appearance.current())
            onView(withId(R.id.launcherApply)).perform(reveal(), click())
            awaitUi(withId(android.R.id.button1)); onView(withId(android.R.id.button1)).perform(click())
            waitFor("Confirmed clock choice must persist") { appearance.current() == LauncherAppearance.Style.CLOCK }
            scenario.recreate(); awaitUi(withId(R.id.launcherClock))
            onView(withId(R.id.launcherClock)).check(matches(isChecked()))
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val bytes = java.io.ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)); bitmap.recycle()
            ContinuousTestEvidence.write(app, "launcher-appearance-api${Build.VERSION.SDK_INT}.png", "image/png", bytes.toByteArray())
        }
        } finally {
            if (Build.VERSION.SDK_INT >= 33) instrumentation.runOnMainSync { localeManager!!.applicationLocales = previousLocales!! }
        }
    }
    @Test fun configuredParentPinProtectsAppearanceSettings() {
        GuardianGate.create(app, "135790"); GuardianGate.lock()
        ActivityScenario.launch(LauncherAppearanceActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.fsGuardianPin))
            // The PIN dialog is the focused Espresso root. Inspect the protected
            // activity root directly rather than searching for it inside the dialog.
            scenario.onActivity {
                assertEquals(android.view.View.INVISIBLE, it.findViewById<android.view.View>(android.R.id.content).visibility)
                assertFalse(GuardianGate.unlocked())
            }
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals(LauncherAppearance.Style.ORIGINAL, appearance.current())
        }
    }
    @Test fun launcherChangesDoNotKillTheLiveCameraOrChangeItsSession() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
            try {
                scenario.onActivity {
                    it.startForegroundService(Intent(it, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                        .putExtra(BackgroundCameraService.EXTRA_CONTINUOUS, true).putExtra(BackgroundCameraService.EXTRA_SEGMENT_SECONDS, 60)
                        .putExtra(BackgroundCameraService.EXTRA_AUDIO, false).putExtra(BackgroundCameraService.EXTRA_RECIPIENT, ""))
                }
                waitFor("Camera must start before changing the launcher") {
                    BackgroundCameraService.activeContinuousSession != null && BackgroundCameraService.state.stage == BackgroundCameraService.Companion.Stage.RECORDING
                }
                val session = requireNotNull(BackgroundCameraService.activeContinuousSession)
                val pid = android.os.Process.myPid()
                for (style in LauncherAppearance.Style.entries) {
                    appearance.select(style).getOrThrow(); Thread.sleep(1000)
                    assertEquals(pid, android.os.Process.myPid())
                    assertEquals(session, BackgroundCameraService.activeContinuousSession)
                    assertEquals(BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.state.stage)
                }
                scenario.onActivity { it.startService(Intent(it, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP)) }
                waitFor("User stop must finalize the same session") { BackgroundCameraService.activeContinuousSession == null }
                ContinuousSessionStore.executor.submit {}.get()
                val files = requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)).listFiles().orEmpty()
                    .filter { it.name.startsWith("NEXVARY_${session}_") && it.extension == "mp4" }
                assertEquals(1, files.size)
                val extractor = MediaExtractor()
                try { extractor.setDataSource(files.single().absolutePath); assertTrue(extractor.trackCount > 0); extractor.selectTrack(0); assertTrue(extractor.sampleTime >= 0) }
                finally { extractor.release() }
            } finally {
                if (BackgroundCameraService.activeContinuousSession != null) app.startService(Intent(app, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
            }
        }
    }
}
