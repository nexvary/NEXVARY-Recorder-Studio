package com.nexvary.recorder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.FamilyStore
import com.nexvary.recorder.family.GuardianGate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreAndGuideTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() {
        GuardianGate.lock()
        context.getSharedPreferences("guardian_mode", 0).edit().clear().commit()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    @After fun clear() = clean()

    @Test fun everyMoreCardOpensItsRealPageAndBackReturnsToCards() {
        ActivityScenario.launch(MoreActivity::class.java).use {
            for (card in listOf(R.id.moreGuide, R.id.moreSettings, R.id.moreMedia, R.id.moreLibrary, R.id.moreCamera,
                R.id.moreCalls, R.id.moreEvidence, R.id.moreFamily, R.id.moreRescue, R.id.moreLanguage, R.id.moreAbout)) {
                onView(withId(card)).perform(reveal(), click())
                awaitUi(withId(R.id.btnBack))
                onView(withId(R.id.btnBack)).perform(reveal(), click())
                awaitUi(withId(R.id.moreGuide))
            }
            onView(withId(R.id.moreTheme)).perform(reveal(), click())
            awaitUi(withText(R.string.theme_electric_blue))
            onView(withId(android.R.id.button2)).perform(click())
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
    @Test fun themeAndLanguageCardsCannotBypassTheGuardianPin() {
        GuardianGate.create(context, "713926"); GuardianGate.lock()
        val theme = com.nexvary.recorder.ui.ThemeManager.currentIndex(context)
        ActivityScenario.launch(MoreActivity::class.java).use {
            for (card in listOf(R.id.moreTheme, R.id.moreLanguage)) {
                onView(withId(card)).perform(reveal(), click())
                awaitUi(withId(R.id.fsGuardianPin))
                onView(withId(android.R.id.button2)).perform(click())
                awaitUi(withId(R.id.moreGuide))
            }
        }
        assertEquals(theme, com.nexvary.recorder.ui.ThemeManager.currentIndex(context))
    }
    @Test fun offlineGuideSearchRotationAndAccordionDoNotCreateAnEmergency() {
        val query = context.getString(R.string.guide_q_free)
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            onView(withId(R.id.guideSearch)).perform(replaceText(query), closeSoftKeyboard())
            awaitUi(withId(R.id.guideFaqFree))
            onView(withText(R.string.guide_a_free)).perform(reveal()).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.guideSearch)).perform(reveal()).check(matches(withText(query)))
            onView(withId(R.id.guideResults)).check(matches(withText(context.getString(R.string.guide_results, 1))))
            onView(withId(R.id.guideFaqFree)).perform(reveal(), click())
            onView(withText(R.string.guide_a_free)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.guideSearch)).perform(reveal(), replaceText("zz-no-guide-topic-90371"), closeSoftKeyboard())
            onView(withText(R.string.guide_empty)).perform(reveal()).check(matches(isDisplayed()))
        }
        val state = FamilyStore(context).snapshot()
        assertNull(FamilyStore.state(state))
        assertEquals(0, com.nexvary.recorder.guardian.CloudOutbox.events(state).length())
    }
    @Test fun largeTextUsesSingleColumnAndKeepsAllCardsReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = android.provider.Settings.System.getFloat(context.contentResolver, "font_scale", 1f)
        fun setScale(value: Float) {
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $value").use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (kotlin.math.abs(context.resources.configuration.fontScale - value) > 0.05f && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
            assertEquals(value, context.resources.configuration.fontScale, 0.05f)
        }
        try {
            setScale(1.5f)
            ActivityScenario.launch(MoreActivity::class.java).use { scenario ->
                awaitUi(withId(R.id.moreGuide))
                scenario.onActivity { activity ->
                    val guide = activity.findViewById<android.view.View>(R.id.moreGuide)
                    val settings = activity.findViewById<android.view.View>(R.id.moreSettings)
                    assertTrue("Large text cards should occupy separate rows", guide.parent !== settings.parent)
                }
                onView(withId(R.id.moreAbout)).perform(reveal()).check(matches(isDisplayed()))
            }
        } finally { setScale(original) }
    }
}
