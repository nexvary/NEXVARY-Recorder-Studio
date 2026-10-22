package com.nexvary.recorder

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.delivery.SmtpSettingsActivity
import com.nexvary.recorder.family.*
import com.nexvary.recorder.guardian.CloudSettingsActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenshotTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before @After fun clean() {
        GuardianGate.lock()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }

    private fun <T : Activity> awaitWindow(scenario: ActivityScenario<T>, secure: Boolean, visible: Boolean = true) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        do {
            var matches = false
            scenario.onActivity {
                matches = (it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) == secure &&
                    (it.findViewById<View>(android.R.id.content).visibility == View.VISIBLE) == visible
            }
            if (matches) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Expected secure=$secure, content visible=$visible")
    }

    @Test fun settingsAndServicePagesAllowCaptureAfterOpeningAndRecreation() {
        val pages = listOf(SettingsActivity::class.java, FamilySettingsActivity::class.java,
            CloudSettingsActivity::class.java, SmtpSettingsActivity::class.java)
        for (page in pages) {
            ActivityScenario.launch(page).use { scenario ->
                awaitWindow(scenario, secure = false)
                scenario.recreate()
                awaitWindow(scenario, secure = false)
            }
        }
    }

    @Test fun pinGateStaysProtectedUntilAuthorizedThenAllowsSettingsCapture() {
        GuardianGate.create(context, "123456")
        GuardianGate.lock()
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.fsGuardianPin))
            awaitWindow(scenario, secure = true, visible = false)
            onView(withId(R.id.fsGuardianPin)).perform(replaceText("123456"))
            onView(withId(android.R.id.button1)).perform(click())
            awaitWindow(scenario, secure = false)
            GuardianGate.lock()
            scenario.recreate()
            awaitUi(withId(R.id.fsGuardianPin))
            awaitWindow(scenario, secure = true, visible = false)
        }
    }

    @Test fun sensitiveFamilyPagesKeepTheirCaptureProtection() {
        for (page in listOf(ParentSettingsActivity::class.java, TrustedContactsActivity::class.java,
            EmergencyProfileActivity::class.java)) {
            ActivityScenario.launch(page).use { scenario -> awaitWindow(scenario, secure = true) }
        }
    }
}
