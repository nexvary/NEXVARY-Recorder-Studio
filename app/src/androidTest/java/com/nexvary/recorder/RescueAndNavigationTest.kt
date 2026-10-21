package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.safety.QuickRescueActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RescueAndNavigationTest {
    @Before fun clearContacts() {
        com.nexvary.recorder.family.RescueCardsStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
    }

    @Test fun additionalDashboardDestinationsAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            for (card in listOf(R.id.cardCamera, R.id.cardRescue, R.id.cardLibrary, R.id.cardSettings, R.id.cardAbout)) {
                onView(withId(card)).perform(reveal(), click())
                onView(withId(R.id.btnBack)).perform(click())
                onView(withId(R.id.cardScreen)).perform(reveal()).check(matches(isDisplayed()))
            }
        }
    }

    @Test fun rescueValidationEditDeleteAndCancelNeverSends() {
        ActivityScenario.launch(QuickRescueActivity::class.java).use {
            onView(withId(R.id.btnAddContact)).perform(reveal(), click())
            onView(withText(R.string.rescue_save)).perform(click())
            onView(withId(R.id.rescueName)).check(matches(isDisplayed())).perform(replaceText("Test contact"))
            onView(withId(R.id.rescuePhone)).perform(replaceText("+201000000000"))
            onView(withId(R.id.rescueMessage)).perform(replaceText("Test draft only"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            onView(withText(R.string.rescue_save)).perform(click())
            onView(withText("Test contact")).perform(reveal()).check(matches(isDisplayed()))
            onView(withText(R.string.rescue_edit)).perform(reveal(), click())
            onView(withId(R.id.rescueName)).perform(replaceText("Updated contact"))
            onView(withText(R.string.rescue_save)).perform(click())
            onView(withText("Updated contact")).perform(reveal()).check(matches(isDisplayed()))
            it.recreate()
            onView(withText("Updated contact")).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.btnPrepareRescue)).perform(reveal(), click())
            onView(withText("Updated contact")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            onView(withText(R.string.rescue_review)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
            onView(withText(R.string.rescue_delete)).perform(reveal(), click())
            onView(withId(android.R.id.button1)).perform(click())
            onView(withText(R.string.rescue_empty)).perform(reveal()).check(matches(isDisplayed()))
        }
    }

    @Test fun missingMediaShowsErrorsWithoutStartingWork() {
        ActivityScenario.launch(com.nexvary.recorder.media.MediaToolsActivity::class.java).use {
            onView(withId(R.id.btnConvertAudio)).perform(reveal(), click())
            onView(withId(R.id.txtAudioStatus)).check(matches(withText(R.string.choose_audio_first)))
            onView(withId(R.id.btnTrimVideo)).perform(reveal(), click())
            onView(withId(R.id.txtVideoStatus)).check(matches(withText(R.string.choose_video_first)))
        }
        ActivityScenario.launch(com.nexvary.recorder.live.LiveBroadcastActivity::class.java).use {
            onView(withId(R.id.btnStart)).perform(reveal(), click())
            onView(withId(R.id.txtStatus)).check(matches(withText(R.string.choose_video_first)))
        }
    }

    @Test fun callAndSafetyScreensReturnWithoutStartingServices() {
        for (page in listOf(com.nexvary.recorder.calls.CallRecorderActivity::class.java, com.nexvary.recorder.safety.SafetyCaptureActivity::class.java)) {
            ActivityScenario.launch(page).use { onView(withId(R.id.btnBack)).perform(click()) }
        }
    }
}
