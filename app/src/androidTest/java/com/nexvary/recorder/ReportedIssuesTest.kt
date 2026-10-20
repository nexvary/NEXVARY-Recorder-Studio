package com.nexvary.recorder

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.media.RecordingsLibraryActivity
import com.nexvary.recorder.media.VaultStore
import com.nexvary.recorder.safety.QuickRescueActivity
import com.nexvary.recorder.ui.ThemeManager
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportedIssuesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(matcher: Matcher<View>) {
        val end = android.os.SystemClock.uptimeMillis() + 20_000
        var error: Throwable? = null
        while (android.os.SystemClock.uptimeMillis() < end) {
            try { onView(matcher).check(matches(org.hamcrest.Matchers.anything())); return }
            catch (e: androidx.test.espresso.NoMatchingViewException) { error = e }
            catch (e: AssertionError) { error = e }
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $matcher", error)
    }
    @Test fun sosEditorUsesNeonForTypedTextHintsAndActions() {
        ActivityScenario.launch(QuickRescueActivity::class.java).use {
            onView(withId(R.id.btnAddContact)).perform(reveal(), click())
            onView(withId(R.id.rescueMessage)).check { view, error ->
                if (error != null) throw error
                val field = view as EditText
                assertEquals(0xFF43FF88.toInt(), field.currentTextColor)
                assertEquals(0xFF9BF6B8.toInt(), field.currentHintTextColor)
            }
            onView(withId(android.R.id.button1)).check { view, _ -> assertEquals(0xFF43FF88.toInt(), (view as android.widget.TextView).currentTextColor) }
            onView(withId(android.R.id.button2)).perform(click())
        }
    }
    @Test fun selectedThemeChangesHomeAndSurvivesReturningFromSettings() {
        ThemeManager.select(context, 0)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.cardSettings)).perform(reveal(), click())
                onView(withText(R.string.choose_theme)).perform(click())
                onView(withText(R.string.theme_purple)).perform(click())
                onView(withId(R.id.btnBack)).perform(click())
                await(withId(R.id.cardScreen))
                scenario.onActivity { activity ->
                    val value = android.util.TypedValue()
                    activity.theme.resolveAttribute(R.attr.nexvaryBackground, value, true)
                    assertEquals(0xFF12091F.toInt(), value.data)
                    val root = activity.findViewById<View>(R.id.dashboardScroll).parent as View
                    val background = root.background
                    background.setBounds(0, 0, 40, 40)
                    val bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
                    background.draw(android.graphics.Canvas(bitmap))
                    // Far corner is the selected purple base; old hard-coded black fails.
                    val color = bitmap.getPixel(39, 39)
                    assertTrue(android.graphics.Color.blue(color) > android.graphics.Color.green(color))
                    bitmap.recycle()
                }
            }
        } finally { ThemeManager.select(context, 0) }
    }
    @Test fun vaultAuthenticatesPinEncryptsChunksAndRejectsTampering() {
        val directory = File(context.cacheDir, "vault-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        try {
            val store = VaultStore(isolated)
            val session = store.create("638291".toCharArray())
            assertNull(store.unlock("000000".toCharArray()))
            val reopened = checkNotNull(store.unlock("638291".toCharArray()))
            val bytes = ByteArray(900_001) { (it % 251).toByte() }
            val entry = store.import(session, "private-marker.mp4", "video/mp4", ByteArrayInputStream(bytes))
            assertEquals(entry, store.entries(reopened).single())
            val out = ByteArrayOutputStream(); store.export(reopened, entry, out)
            assertArrayEquals(bytes, out.toByteArray())
            val encrypted = File(directory, "recording_vault/${entry.id}.nvx")
            assertFalse(encrypted.readBytes().toString(Charsets.ISO_8859_1).contains("private-marker"))
            val corrupted = encrypted.readBytes(); corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte(); encrypted.writeBytes(corrupted)
            assertTrue(runCatching { store.export(reopened, entry, ByteArrayOutputStream()) }.isFailure)
            store.delete(entry); assertTrue(store.entries(reopened).isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun libraryHideLockWrongPinUnlockRestoreAndDelete() {
        val vaultDir = File(context.noBackupFilesDir, "recording_vault")
        check(!File(vaultDir, "access.properties").exists()) { "This fixture requires a fresh emulator install; never run it on user data." }
        val name = "ui-vault-fixture-${System.nanoTime()}.mp4"
        val fixture = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), name)
        val data = ByteArray(600_001) { (it % 239).toByte() }
        fixture.writeBytes(data)
        try {
            ActivityScenario.launch(RecordingsLibraryActivity::class.java).use {
                await(withText(name))
                onView(withText(R.string.library_hide)).perform(reveal(), click())
                onView(withId(R.id.vaultPin)).perform(replaceText("638291"))
                onView(withId(R.id.vaultConfirmPin)).perform(replaceText("638291"), closeSoftKeyboard())
                onView(withId(android.R.id.button1)).perform(click())
                await(withText(R.string.vault_hide_confirm))
                onView(withId(android.R.id.button1)).perform(click())
                await(withText(R.string.vault_restore))
                assertFalse(fixture.exists())
                onView(withId(R.id.openVault)).perform(reveal(), click())
                await(withText(R.string.library_empty))
                onView(withId(R.id.openVault)).perform(reveal(), click())
                onView(withId(R.id.vaultPin)).perform(replaceText("000000"), closeSoftKeyboard())
                onView(withId(android.R.id.button1)).perform(click())
                await(org.hamcrest.Matchers.allOf(withId(R.id.vaultPin), hasErrorText(context.getString(R.string.vault_pin_failed))))
                onView(withId(R.id.vaultPin)).perform(replaceText("638291"), closeSoftKeyboard())
                onView(withId(android.R.id.button1)).perform(click())
                await(withText(R.string.vault_restore))
                onView(withText(R.string.vault_restore)).perform(reveal(), click())
                onView(withId(android.R.id.button1)).perform(click())
                await(withText(R.string.vault_empty))
                onView(withId(R.id.openVault)).perform(reveal(), click())
                await(withText(name))
                // Check restored bytes, then exercise the actual delete button and confirmation.
                val uri = restoredUri(name)
                assertArrayEquals(data, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                onView(withText(R.string.library_delete)).perform(reveal(), click())
                onView(withId(android.R.id.button1)).perform(click())
                await(withText(R.string.library_empty))
                assertNull(findRestored(name))
            }
        } finally {
            fixture.delete(); findRestored(name)?.let { context.contentResolver.delete(it, null, null) }
            vaultDir.deleteRecursively()
        }
    }
    private fun restoredUri(name: String) = checkNotNull(findRestored(name))
    private fun findRestored(name: String): android.net.Uri? {
        val collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(collection, arrayOf("_id"), "_display_name = ?", arrayOf(name), null)?.use {
            if (it.moveToFirst()) return android.content.ContentUris.withAppendedId(collection, it.getLong(0))
        }
        return null
    }
}
