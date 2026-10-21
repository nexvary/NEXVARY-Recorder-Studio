package com.nexvary.recorder

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.guardian.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceSetupTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() { GuardianGate.lock();FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) } }
    @After fun clear()=clean()
    @Test fun institutionSetupUsesOnlyItsAddressAndRejectsAnInsecureConnection() {
        ActivityScenario.launch(CloudSettingsActivity::class.java).use {
            awaitUi(withId(R.id.guardianInstitutionService))
            onView(withId(R.id.guardianApi)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.guardianInstitutionService)).perform(reveal(),click())
            onView(withId(R.id.guardianInstitutionUrl)).perform(reveal(),replaceText("http://school.example"),closeSoftKeyboard())
            onView(withId(R.id.guardianConnectInstitution)).perform(reveal(),click())
            onView(withText(R.string.guardian_invalid_config)).perform(reveal()).check(matches(isDisplayed()))
            assertFalse(CloudApi.configured(context))
        }
    }
    @Test fun serviceSwitchRevokesOldJobsWithoutDeletingLocalEvidence() {
        FamilyStore(context).update { root ->
            root.put("cloud",JSONObject().put("baseUrl","https://old.example").put("idToken","synthetic-old-token"))
            root.put("evidence",JSONObject().put("local.mp4",JSONObject().put("eventId","local-event")))
            root.put("verifiedEntitlement",JSONObject().put("state","ACTIVE"))
            CloudOutbox.events(root).put("old",JSONObject().put("status","UPLOADING"))
            CloudApi.installService(root,JSONObject().put("authType","NEXVARY").put("baseUrl","https://school.example")
                .put("service",JSONObject().put("schemaVersion",1).put("edition","INSTITUTION").put("name","QA school")))
        }
        val root=FamilyStore(context).snapshot()
        assertEquals("https://school.example",CloudApi.config(root).getString("baseUrl"))
        assertFalse(CloudApi.config(root).has("idToken"));assertFalse(root.has("verifiedEntitlement"))
        assertEquals("CANCELLED",CloudOutbox.events(root).getJSONObject("old").getString("status"))
        assertTrue(root.getJSONObject("evidence").has("local.mp4"))
    }
    @Test fun legacyAwsFieldsAreExplicitlyAdvancedAndLocalSosStaysAvailable() {
        ActivityScenario.launch(CloudSettingsActivity::class.java).use {
            awaitUi(withId(R.id.guardianAdvancedConnection))
            onView(withId(R.id.guardianAdvancedConnection)).perform(reveal(),click())
            onView(withId(R.id.guardianRegion)).perform(reveal()).check(matches(isDisplayed()))
            onView(withId(R.id.guardianClient)).perform(reveal()).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(GuardianEmergencyActivity::class.java).use {
            awaitUi(withText(R.string.fs_sos))
            onView(withText(R.string.fs_sos)).perform(reveal()).check(matches(isEnabled()))
        }
        assertNull(FamilyStore.state(FamilyStore(context).snapshot()))
    }
}
