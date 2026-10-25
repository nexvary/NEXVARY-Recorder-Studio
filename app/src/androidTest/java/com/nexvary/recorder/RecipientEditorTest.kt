package com.nexvary.recorder

import android.os.Build
import android.os.Environment
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.delivery.*
import com.nexvary.recorder.family.*
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real input/add/IME/remove/rotation/retry interactions; no SMTP connection is made. */
@RunWith(AndroidJUnit4::class)
class RecipientEditorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var active = false
    @Before fun prepare() {
        Assume.assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish")); active = true
        SmtpSettings(context).clear(); GuardianGate.lock(); FamilyStore(context).update { it.remove("guardianPin") }
        context.getSharedPreferences("camera_options", 0).edit().remove("recipient").commit()
    }
    @After fun clean() {
        if (!active) return
        SmtpSettings(context).clear(); FamilyStore(context).update { it.remove("guardianPin") }; GuardianGate.lock()
    }
    private fun enter(address: String) = onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText(address), closeSoftKeyboard())
    private fun add(address: String) { enter(address); onView(withId(R.id.recipientAdd)).perform(reveal(), click()) }
    private fun chip(address: String) = onView(allOf(isAssignableFrom(Chip::class.java), withText(address)))
    private fun remove(address: String) {
        chip(address).perform(reveal(), GeneralClickAction(Tap.SINGLE, CoordinatesProvider { view ->
            val xy = IntArray(2); view.getLocationOnScreen(xy)
            floatArrayOf(xy[0] + view.width - 20 * view.resources.displayMetrics.density, xy[1] + view.height / 2f)
        }, Press.FINGER))
    }
    @Test fun separateAddressActionsValidateDuplicatesRemoveAndPreserveRotationDraft() {
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use { scenario ->
            onView(withId(R.id.cameraEmail)).perform(reveal(), click())
            add("one@example.invalid")
            onView(withId(R.id.cameraRecipient)).check(matches(withText("")))
            onView(withId(R.id.cameraRecipient)).perform(reveal(), replaceText("two@example.invalid"), pressImeActionButton(), closeSoftKeyboard())
            chip("one@example.invalid").perform(reveal()).check(matches(isDisplayed()))
            chip("two@example.invalid").perform(reveal()).check(matches(isDisplayed()))
            add("ONE@example.invalid")
            onView(withId(R.id.cameraRecipient)).check(matches(hasErrorText(context.getString(R.string.recipient_duplicate))))
            enter(""); remove("one@example.invalid")
            chip("one@example.invalid").check(doesNotExist())
            enter("unfinished@")
            scenario.recreate(); awaitUi(withId(R.id.cameraRecipient))
            onView(withId(R.id.cameraRecipient)).perform(reveal()).check(matches(withText("unfinished@")))
            chip("two@example.invalid").perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.recipientCount)).check(matches(withText(context.getString(R.string.recipient_count, 1, 10))))
            enter(""); remove("two@example.invalid")
            onView(withId(R.id.recipientCount)).check(matches(withText(context.getString(R.string.recipient_count, 0, 10))))
        }
    }
    @Test fun invalidOrSeparatorBasedInputDoesNotBecomeARecipientAndTenDisablesAdd() {
        ActivityScenario.launch(BackgroundCameraActivity::class.java).use {
            onView(withId(R.id.cameraEmail)).perform(reveal(), click())
            for (invalid in listOf("bad", "one@example.invalid;two@example.invalid")) {
                add(invalid)
                onView(withId(R.id.cameraRecipient)).check(matches(hasErrorText(context.getString(R.string.recipient_single_invalid))))
                onView(withId(R.id.recipientCount)).check(matches(withText(context.getString(R.string.recipient_count, 0, 10))))
            }
            for (n in 1..10) add("person$n@example.invalid")
            onView(withId(R.id.recipientAdd)).perform(reveal()).check(matches(isNotEnabled()))
            onView(withId(R.id.cameraRecipient)).check(matches(isNotEnabled()))
            onView(withId(R.id.recipientCount)).perform(reveal()).check(matches(withText(context.getString(R.string.recipient_count, 10, 10))))
            remove("person1@example.invalid")
            onView(withId(R.id.recipientAdd)).perform(reveal()).check(matches(isEnabled()))
            add("replacement@example.invalid")
            chip("replacement@example.invalid").perform(reveal()).check(matches(isDisplayed()))
        }
    }
    @Test fun outboxRetryUsesTheSameCardsAndCancellationKeepsOriginalRecipients() {
        SmtpSettings(context).save(SmtpProfile("smtp.example.invalid", 587, false, "fixture", "fixture-only", "sender@example.invalid", 25))
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "recipient-editor-${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(1024)) }
        val queue = MailQueue(context); val job = queue.enqueue(file, "one@example.invalid, two@example.invalid", 25)
        queue.claim(job.id); queue.finish(job.id, MailQueue.State.CONFIG)
        try {
            ActivityScenario.launch(MailOutboxActivity::class.java).use {
                awaitUi(withText(file.name))
                onView(allOf(withId(R.id.mailRetry), withParent(hasDescendant(withText(file.name))))).perform(reveal(), click())
                awaitUi(withId(R.id.recipientAdd))
                chip("one@example.invalid").perform(reveal()).check(matches(isDisplayed()))
                chip("two@example.invalid").perform(reveal()).check(matches(isDisplayed()))
                add("three@example.invalid")
                chip("three@example.invalid").perform(reveal()).check(matches(isDisplayed()))
                onView(withId(android.R.id.button2)).perform(click())
                assertEquals(job.recipient, queue.get(job.id)?.recipient)
                assertEquals(MailQueue.State.CONFIG, queue.get(job.id)?.state)
                assertTrue(file.exists()); assertTrue(queue.attachment(job.id).exists())
            }
        } finally { queue.cancel(job.id); file.delete() }
    }
}
