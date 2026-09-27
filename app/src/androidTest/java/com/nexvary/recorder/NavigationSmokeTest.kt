package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @Test
    fun everyDashboardPageOpensAndBackReturnsHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openAndReturn(R.id.cardScreen)
            openAndReturn(R.id.cardVoice)
            openAndReturn(R.id.cardReplace)
            openAndReturn(R.id.cardLive)
        }
    }

    @Test
    fun bottomNavigationItemsAreConnected() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navScreen)).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navVoice)).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navLive)).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.language_title)).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.about_title)).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.replace_audio_title)).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())
        }
    }

    @Test
    fun themePickerOpensFromFixedHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnThemePicker)).check(matches(isDisplayed())).perform(click())
            onView(withText(R.string.choose_theme)).check(matches(isDisplayed()))
            onView(withText(R.string.theme_electric_blue)).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun screenRecorderProfessionalControlsExist() {
        ActivityScenario.launch(
            com.nexvary.recorder.screen.ScreenRecorderActivity::class.java
        ).use {
            onView(withId(R.id.switchMic)).check(matches(isDisplayed()))
            onView(withId(R.id.switchFloating)).check(matches(isDisplayed()))
            onView(withId(R.id.switchTouches)).check(matches(isDisplayed()))
            onView(withId(R.id.switchCamera)).check(matches(isDisplayed()))
            onView(withId(R.id.groupCountdown)).check(matches(isDisplayed()))
            onView(withId(R.id.btnStorage)).check(matches(isDisplayed()))
            onView(withId(R.id.btnStart)).check(matches(isDisplayed()))
            onView(withId(R.id.btnPauseResume)).check(matches(isDisplayed()))
            onView(withId(R.id.btnStop)).check(matches(isDisplayed()))
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
