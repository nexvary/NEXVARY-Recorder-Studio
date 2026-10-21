package com.nexvary.recorder

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.FamilyStore
import com.nexvary.recorder.family.GuardianGate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AllPagesReviewTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() {
        GuardianGate.lock()
        context.getSharedPreferences("guardian_mode", 0).edit().clear().commit()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    @After fun clear() = clean()

    @Test fun everyRegisteredInternalActivityRendersWithSafeBackNavigation() {
        @Suppress("DEPRECATION")
        val pages = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .map { it.name }.filter { it.startsWith("com.nexvary.recorder.") }.sorted()
        assertTrue("Manifest unexpectedly contains too few pages", pages.size >= 25)
        for (name in pages) {
            @Suppress("UNCHECKED_CAST")
            val page = Class.forName(name) as Class<out Activity>
            ActivityScenario.launch(page).use {
                val id = when (name) {
                    MainActivity::class.java.name -> R.id.cardScreen
                    com.nexvary.recorder.guardian.ChildHomeActivity::class.java.name -> R.id.btnBack
                    else -> R.id.btnBack
                }
                if (name == com.nexvary.recorder.guardian.ChildHomeActivity::class.java.name) {
                    awaitUi(withText(R.string.fs_sos))
                    onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                } else {
                    awaitUi(withId(id))
                    onView(withId(id)).perform(reveal()).check(matches(isDisplayed()))
                }
            }
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
        assertNull(com.nexvary.recorder.camera.BackgroundCameraService.activeOutputPath)
        assertNull(com.nexvary.recorder.family.AudioEvidenceService.activeEvent)
    }

    @Test fun voiceStudioCreatesARealPlayableWavAndListsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.RECORD_AUDIO)
        fun recordings(): Map<Long, String> {
            val rows = mutableMapOf<Long, String>()
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf("_id", "_display_name"), "relative_path LIKE ? AND _display_name LIKE ?", arrayOf("%/NEXVARY Recorder/%", "voice_%.wav"), null)?.use { cursor -> while(cursor.moveToNext()) rows[cursor.getLong(0)] = cursor.getString(1) }
            return rows
        }
        val before = recordings().keys
        try {
            ActivityScenario.launch(com.nexvary.recorder.audio.VoiceRecorderActivity::class.java).use {
                onView(withId(R.id.btnRecord)).perform(reveal(), androidx.test.espresso.action.ViewActions.click())
                awaitUi(withText(R.string.voice_recording))
                android.os.SystemClock.sleep(3000)
                onView(withId(R.id.btnStop)).perform(reveal(), androidx.test.espresso.action.ViewActions.click())
                awaitUi(withText(R.string.voice_complete))
            }
            val saved = recordings().filterKeys { it !in before }
            assertEquals(1, saved.size)
            val uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, saved.keys.single())
            val metadata = android.media.MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                assertTrue((metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) >= 2000)
            } finally { metadata.release() }
            ActivityScenario.launch(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java).use { awaitUi(withText(saved.values.single())) }
        } finally {
            recordings().keys.filter { it !in before }.forEach { context.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it), null, null) }
        }
    }

    @Test fun exportedMediaAndLegacyFolderBothAppearInRecordingLibrary() {
        val sample = File(context.cacheDir, "library-export-qa.mp4")
        val owned = mutableListOf<android.net.Uri>()
        InstrumentationRegistry.getInstrumentation().context.assets.open("qa-sample.mp4").use { input -> sample.outputStream().use { input.copyTo(it) } }
        try {
            ActivityScenario.launch(com.nexvary.recorder.media.MediaCenterActivity::class.java).use { scenario ->
                lateinit var page: com.nexvary.recorder.media.MediaCenterActivity
                scenario.onActivity { page = it }
                owned.add(page.publish(sample, "video/mp4", "mp4"))
            }
            val legacyName = "QA-legacy-${java.util.UUID.randomUUID()}.mp4"
            val legacy = checkNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, legacyName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/NEXVARY/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            owned.add(legacy)
            checkNotNull(context.contentResolver.openOutputStream(legacy)).use { output -> sample.inputStream().use { it.copyTo(output) } }
            context.contentResolver.update(legacy, android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val publishedName = context.contentResolver.query(owned.first(), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use { it.moveToFirst(); it.getString(0) }
            ActivityScenario.launch(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java).use {
                awaitUi(withText(publishedName))
                onView(withText(publishedName)).perform(reveal()).check(matches(isDisplayed()))
                onView(withText(legacyName)).perform(reveal()).check(matches(isDisplayed()))
                onView(org.hamcrest.Matchers.allOf(withText(R.string.guardian_share_export), withParent(hasDescendant(withText(publishedName))))).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            }
        } finally {
            owned.forEach { context.contentResolver.delete(it, null, null) }
            sample.delete()
        }
    }
}
