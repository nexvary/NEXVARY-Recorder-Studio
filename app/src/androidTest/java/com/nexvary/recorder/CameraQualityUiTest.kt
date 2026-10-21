package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.delivery.SmtpProfile
import com.nexvary.recorder.delivery.SmtpSettings
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraQualityUiTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun clear() { context.getSharedPreferences("camera_options",0).edit().remove("quality").commit() }
    @Test fun compactIsDefaultAndUserQualityChoiceSurvivesRotationAndReopening() {
        clear()
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
                onView(withId(R.id.cameraQualityCompact)).perform(reveal()).check(matches(isChecked()))
                onView(withId(R.id.cameraQualityBalanced)).perform(reveal(), click())
                it.recreate(); awaitUi(withId(R.id.cameraQualityBalanced))
                onView(withId(R.id.cameraQualityBalanced)).perform(reveal()).check(matches(isChecked()))
            }
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
                onView(withId(R.id.cameraQualityBalanced)).perform(reveal()).check(matches(isChecked()))
            }
        } finally { clear() }
    }
    @Test fun fiveMinuteHighQualityWarnsAboutSmtpLimitAndCompactClearsWarning() {
        clear(); val settings = SmtpSettings(context)
        settings.save(SmtpProfile("mail.example.invalid",587,false,"fixture","fixture-only","from@example.invalid",25))
        try {
            ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
                onView(withId(R.id.cameraTimer)).perform(reveal(),click())
                onView(withId(R.id.cameraMinutes)).perform(reveal(),replaceText("5"),closeSoftKeyboard())
                onView(withId(R.id.cameraEmail)).perform(reveal(),click())
                awaitUi(withText(containsString(context.getString(R.string.camera_quality_limit,25))))
                onView(withId(R.id.cameraQualityHigh)).perform(reveal(),click())
                onView(withId(R.id.cameraQualityEstimate)).perform(reveal()).check(matches(withText(containsString(context.getString(R.string.camera_quality_over_limit)))))
                onView(withId(R.id.cameraQualityCompact)).perform(reveal(),click())
                onView(withId(R.id.cameraQualityEstimate)).perform(reveal()).check(matches(not(withText(containsString(context.getString(R.string.camera_quality_over_limit))))))
            }
        } finally { clear(); settings.clear() }
    }
}
