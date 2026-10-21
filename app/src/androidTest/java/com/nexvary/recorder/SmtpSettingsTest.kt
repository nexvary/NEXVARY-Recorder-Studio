package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.delivery.SmtpSettings
import com.nexvary.recorder.delivery.SmtpSettingsActivity
import com.nexvary.recorder.delivery.SmtpProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No external server is contacted. These exercise the actual saved settings and security controls. */
@RunWith(AndroidJUnit4::class)
class SmtpSettingsTest {
    private val settings get() = SmtpSettings(InstrumentationRegistry.getInstrumentation().targetContext)
    @Test fun existing465ProfileRepairsSecurityWithoutDiscardingSameAccountPassword() {
        settings.save(SmtpProfile("mail.example.invalid", 465, false, "fixture-user", "fixture-password", "sender@example.invalid", 25))
        try {
            ActivityScenario.launch(SmtpSettingsActivity::class.java).use {
                onView(withId(R.id.smtpTls)).perform(reveal()).check(matches(isChecked()))
                onView(withId(R.id.smtpPassword)).perform(reveal()).check(matches(withText("")))
                onView(withId(R.id.smtpSave)).perform(reveal(), click())
                awaitUi(withText(R.string.smtp_saved))
                assertTrue(settings.load()!!.directTls)
                assertEquals("fixture-password", settings.load()!!.password)
            }
        } finally { settings.clear() }
    }
    @Test fun enteringConventionalPortsUpdatesTlsInBothDirectionsIncludingArabicDigits() {
        settings.clear()
        ActivityScenario.launch(SmtpSettingsActivity::class.java).use {
            onView(withId(R.id.smtpPort)).perform(reveal(), replaceText("465"), closeSoftKeyboard())
            onView(withId(R.id.smtpTls)).perform(reveal()).check(matches(isChecked()))
            onView(withId(R.id.smtpPort)).perform(reveal(), replaceText("587"), closeSoftKeyboard())
            onView(withId(R.id.smtpTls)).perform(reveal()).check(matches(isNotChecked()))
            onView(withId(R.id.smtpPort)).perform(reveal(), replaceText("٤٦٥"), closeSoftKeyboard())
            onView(withId(R.id.smtpTls)).perform(reveal()).check(matches(isChecked()))
            onView(withId(R.id.smtpPort)).perform(reveal(), replaceText("2525"), closeSoftKeyboard())
            onView(withId(R.id.smtpTls)).perform(reveal()).check(matches(isChecked()))
        }
    }
    @Test fun invalidHostShowsSpecificErrorWithoutSavingOrConnecting() {
        settings.clear()
        ActivityScenario.launch(SmtpSettingsActivity::class.java).use {
            onView(withId(R.id.smtpHost)).perform(reveal(), replaceText("mail.example.invalid:465"), closeSoftKeyboard())
            onView(withId(R.id.smtpTest)).perform(reveal(), click())
            onView(withId(R.id.smtpStatus)).perform(reveal()).check(matches(withText(R.string.smtp_host_invalid)))
            assertNull(settings.load())
        }
    }
    @Test fun manualSaveSurvivesReopeningAndBlankPasswordKeepsSameAccountSecret() {
        settings.clear()
        try {
            ActivityScenario.launch(SmtpSettingsActivity::class.java).use {
                listOf(R.id.smtpHost to "mail.example.invalid", R.id.smtpPort to "465", R.id.smtpUser to "fixture-user",
                    R.id.smtpPassword to "fixture-password", R.id.smtpFrom to "sender@example.invalid").forEach { (id, value) ->
                    onView(withId(id)).perform(reveal(), replaceText(value), closeSoftKeyboard())
                }
                onView(withId(R.id.smtpSave)).perform(reveal(), click())
                awaitUi(withText(R.string.smtp_saved))
                assertTrue(settings.load()!!.directTls)
                assertEquals(465, settings.load()!!.port)
            }
            ActivityScenario.launch(SmtpSettingsActivity::class.java).use {
                onView(withId(R.id.smtpPassword)).perform(reveal()).check(matches(withText("")))
                onView(withId(R.id.smtpSave)).perform(reveal(), click())
                awaitUi(withText(R.string.smtp_saved))
                assertEquals("fixture-password", settings.load()!!.password)
            }
        } finally { settings.clear() }
    }
}
