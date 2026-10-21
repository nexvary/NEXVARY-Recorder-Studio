package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
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
            openAndReturn(R.id.cardFamily)
            openAndReturn(R.id.cardReplace)
            openAndReturn(R.id.cardLive)
        }
    }

    @Test
    fun bottomNavigationItemsAreConnected() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navScreen)).perform(click())
            awaitUi(withId(R.id.btnBack))
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navSafety)).perform(click())
            awaitUi(withId(R.id.btnBack))
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navLive)).perform(click())
            awaitUi(withId(R.id.btnBack))
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.language_title)).check(matches(isDisplayed())).perform(click())
            awaitUi(withId(R.id.btnBack))
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.about_title)).check(matches(isDisplayed())).perform(click())
            awaitUi(withId(R.id.btnBack))
            onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.navMore)).perform(click())
            onView(withText(R.string.media_tools_title)).check(matches(isDisplayed())).perform(click())
            awaitUi(withId(R.id.btnBack))
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
            onView(withId(R.id.switchMic)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.switchFloating)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.switchTouches)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.switchCamera)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.groupCountdown)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnStorage))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnStart))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnPauseResume))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnStop))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun mediaToolsControlsAreConnected() {
        ActivityScenario.launch(
            com.nexvary.recorder.media.MediaToolsActivity::class.java
        ).use {
            onView(withId(R.id.btnBack))
                .check(matches(isDisplayed()))
            onView(withId(R.id.btnReplaceAudio))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnChooseAudio))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnConvertAudio))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnChooseVideo))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnTrimVideo))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.btnConvertVideo))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun aboutAndLanguageControlsAreConnected() {
        ActivityScenario.launch(AboutActivity::class.java).use {
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()))
            onView(withId(R.id.btnWebsite)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnFacebook)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnEmail)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnYoutube)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnX)).perform(reveal()).check(matches(isDisplayed()))
        }

        ActivityScenario.launch(LanguageActivity::class.java).use {
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()))
            onView(withId(R.id.btnArabic)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnEnglish)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnTurkish)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnSpanish)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnGerman)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnItalian)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnFrench)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnUrdu)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnPersian)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnRussian)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnArabic)).perform(reveal())
            onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.ScrollView::class.java))
                .perform(androidx.test.espresso.action.ViewActions.swipeUp(), androidx.test.espresso.action.ViewActions.swipeUp())
            onView(withId(R.id.btnRussian)).check(matches(isDisplayed()))
        }
    }

    private fun openAndReturn(cardId: Int) {
        onView(withId(cardId)).perform(reveal(), click())
        onView(withId(R.id.btnBack)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.cardScreen)).perform(reveal()).check(matches(isDisplayed()))
    }
}
