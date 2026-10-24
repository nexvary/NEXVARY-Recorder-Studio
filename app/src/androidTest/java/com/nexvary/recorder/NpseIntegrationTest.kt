package com.nexvary.recorder

import android.content.ComponentName
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.npse.ControlCenterActivity
import com.nexvary.npse.R as NpseR
import com.nexvary.recorder.family.FamilyStore
import com.nexvary.recorder.family.GuardianGate
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NpseIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Before fun clean() {
        GuardianGate.lock()
        FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) }
    }
    @After fun reset() = clean()
    @Test fun libraryIsInternalAndStandardModeWorksWithoutManager() {
        @Suppress("DEPRECATION") val info = context.packageManager.getActivityInfo(ComponentName(context, ControlCenterActivity::class.java), 0)
        assertFalse(info.exported)
        ActivityScenario.launch(ControlCenterActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                fun button(id: Int) = all.filterIsInstance<Button>().single { it.text == activity.getString(id) }
                assertFalse(all.filterIsInstance<Switch>().single().isChecked)
                assertFalse(button(NpseR.string.npse_advanced).isEnabled)
                button(NpseR.string.npse_standard).performClick()
                assertTrue(button(NpseR.string.npse_export).isEnabled)
                assertTrue(all.filterIsInstance<TextView>().any {
                    runCatching { JSONObject(it.text.toString()).optString("schema") == "nexvary-npse/v1" }.getOrDefault(false)
                })
            }
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
    @Test fun moreEntryRequiresConfiguredGuardianPin() {
        GuardianGate.create(context, "713926"); GuardianGate.lock()
        ActivityScenario.launch(MoreActivity::class.java).use {
            onView(withId(R.id.morePrivileged)).perform(reveal(), click())
            awaitUi(withId(R.id.fsGuardianPin))
            onView(withId(android.R.id.button2)).perform(click())
            awaitUi(withId(R.id.morePrivileged))
            onView(withId(R.id.morePrivileged)).perform(reveal()).check(matches(isDisplayed()))
        }
        assertFalse(GuardianGate.unlocked())
    }
}
