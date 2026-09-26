package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @Test
    fun everyInternalPageOpensAndBackReturnsHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openAndReturn(R.id.cardScreen)
            openAndReturn(R.id.cardVoice)
            openAndReturn(R.id.cardReplace)
            openAndReturn(R.id.cardLive)
            openAndReturn(R.id.cardLanguage)
            openAndReturn(R.id.cardAbout)
        }
    }

    @Test
    fun aboutAndLanguageControlsAreConnected() {
        ActivityScenario.launch(AboutActivity::class.java).use {
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()))
            onView(withId(R.id.btnWebsite)).check(matches(isDisplayed()))
            onView(withId(R.id.btnFacebook)).check(matches(isDisplayed()))
            onView(withId(R.id.btnEmail)).check(matches(isDisplayed()))
            onView(withId(R.id.btnYoutube)).check(matches(isDisplayed()))
            onView(withId(R.id.btnX)).check(matches(isDisplayed()))
        }

        ActivityScenario.launch(LanguageActivity::class.java).use {
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()))
            onView(withId(R.id.btnArabic)).check(matches(isDisplayed()))
            onView(withId(R.id.btnEnglish)).check(matches(isDisplayed()))
            onView(withId(R.id.btnTurkish)).check(matches(isDisplayed()))
            onView(withId(R.id.btnSpanish)).check(matches(isDisplayed()))
            onView(withId(R.id.btnGerman)).check(matches(isDisplayed()))
            onView(withId(R.id.btnItalian)).check(matches(isDisplayed()))
            onView(withId(R.id.btnFrench)).check(matches(isDisplayed()))
            onView(withId(R.id.btnUrdu)).check(matches(isDisplayed()))
            onView(withId(R.id.btnPersian)).check(matches(isDisplayed()))
            onView(withId(R.id.btnRussian)).check(matches(isDisplayed()))
        }
    }

    private fun openAndReturn(cardId: Int) {
        onView(withId(cardId)).perform(click())
        onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.cardScreen)).check(matches(isDisplayed()))
    }
}
