package com.nexvary.recorder

import android.content.Intent
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.guardian.CloudSettingsActivity
import com.nexvary.recorder.ui.StudioSpinner
import org.hamcrest.Matchers.anything
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSimplificationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() { GuardianGate.lock();context.getSharedPreferences("guardian_mode",0).edit().clear().commit();FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) } }
    @After fun clear()=clean()
    @Test fun settingsCardsNavigateAndOptionalToolsStayCollapsed() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            awaitUi(withId(R.id.settingsSafety))
            onView(withId(R.id.openSmtp)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.settingsSafety)).perform(reveal(),click())
            onView(withContentDescription(context.getString(R.string.settings_sharing))).perform(reveal(),click())
            awaitUi(withId(R.id.fsDisplayName))
            onView(withId(R.id.btnBack)).perform(reveal(),click())
            onView(withId(R.id.btnBack)).perform(reveal(),click())
            for(card in listOf(R.id.settingsAppearance,R.id.settingsAccount,R.id.settingsHelp)) {
                onView(withId(card)).perform(reveal(),click());awaitUi(withId(R.id.btnBack));onView(withId(R.id.btnBack)).perform(reveal(),click())
            }
            onView(withId(R.id.settingsAdvanced)).perform(reveal(),click())
            onView(withId(R.id.openSmtp)).perform(reveal()).check(matches(isDisplayed()))
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
    @Test fun changingSharingPreservesOtherSettingsAndDoesNotStartAnEmergency() {
        FamilyStore(context).update { it.put("config",JSONObject().put("evidence","VIDEO").put("immediate",true).put("showMedical",true).put("destination",JSONObject().put("name","Existing destination"))) }
        ActivityScenario.launch<FamilySettingsActivity>(Intent(context,FamilySettingsActivity::class.java).putExtra("section","sharing")).use {
            awaitUi(withId(R.id.fsDisplayName));onView(withId(R.id.fsDisplayName)).perform(reveal(),replaceText("QA guardian"),closeSoftKeyboard())
            onView(withId(R.id.fsSaveSettings)).perform(reveal(),click());onView(withId(android.R.id.button1)).perform(click())
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            while(FamilyStore.config(FamilyStore(context).snapshot()).optString("displayName")!="QA guardian" && android.os.SystemClock.elapsedRealtime()<deadline) android.os.SystemClock.sleep(50)
        }
        val root=FamilyStore(context).snapshot();val config=FamilyStore.config(root)
        assertEquals("QA guardian",config.optString("displayName"));assertEquals("VIDEO",config.optString("evidence"));assertTrue(config.optBoolean("immediate"));assertTrue(config.optBoolean("showMedical"));assertEquals("Existing destination",config.getJSONObject("destination").getString("name"));assertNull(FamilyStore.state(root))
    }
    @Test fun timerPopupRowsAreGreenAndCustomDurationSurvivesRecreation() {
        ActivityScenario.launch(FamilyDashboardActivity::class.java).use { scenario ->
            awaitUi(withId(R.id.fsPresets));onView(withId(R.id.fsMinutes)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.fsGrace)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            scenario.onActivity { activity ->
                val spinner=activity.findViewById<Spinner>(R.id.fsPresets)
                assertEquals(StudioSpinner.GREEN,(spinner.selectedView as TextView).currentTextColor)
                for(i in 0 until spinner.adapter.count) assertEquals(StudioSpinner.GREEN,(spinner.adapter.getDropDownView(i,null,spinner) as TextView).currentTextColor)
            }
            onView(withId(R.id.fsPresets)).perform(reveal(),click())
            onData(anything()).atPosition(4).check { view,error -> if(error!=null) throw error;assertEquals(StudioSpinner.GREEN,(view as TextView).currentTextColor) }.perform(click())
            onView(withId(R.id.fsMinutes)).perform(reveal(),replaceText("23"),closeSoftKeyboard())
            scenario.recreate();awaitUi(withId(R.id.fsPresets));onView(withId(R.id.fsMinutes)).perform(reveal()).check(matches(withText("23")))
            onView(withId(R.id.timerOptions)).perform(reveal(),click());onView(withId(R.id.fsGrace)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.fsSos)).perform(reveal()).check(matches(isEnabled()))
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
    @Test fun accountLoginShowsOnlyTheFieldsNeededForEachStep() {
        FamilyStore(context).update { it.put("cloud",JSONObject().put("baseUrl","https://qa.example.invalid").put("authType","NEXVARY")) }
        ActivityScenario.launch(CloudSettingsActivity::class.java).use {
            awaitUi(withId(R.id.guardianEmail))
            onView(withId(R.id.guardianCode)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.guardianAuthSubmit)).perform(reveal()).check(matches(withText(R.string.guardian_login)))
            onView(withText(R.string.guardian_signup)).perform(reveal(),click())
            onView(withId(R.id.guardianAuthSubmit)).perform(reveal()).check(matches(withText(R.string.guardian_signup)))
            onView(withId(R.id.guardianCode)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
}
