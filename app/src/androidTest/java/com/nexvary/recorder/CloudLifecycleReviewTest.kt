package com.nexvary.recorder

import android.os.Environment
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.recorder.family.*
import com.nexvary.recorder.guardian.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudLifecycleReviewTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() { FamilyStore(context).update { root -> root.keys().asSequence().toList().forEach(root::remove) } }
    @After fun clear() = clean()
    private fun tokens(sub: String): JSONObject {
        val token = "test." + Base64.encodeToString(JSONObject().put("sub", sub).toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) + ".test"
        return JSONObject().put("IdToken", token).put("RefreshToken", "synthetic-refresh-only").put("ExpiresIn", 900)
    }
    @Test fun accountSwitchCancelsOldTransportAndClearsPremiumButKeepsLocalEvidence() {
        val root = JSONObject()
        CloudSessionState.install(root, tokens("guardian-a"))
        CloudOutbox.events(root).put("local-event", JSONObject().put("status", "PENDING"))
        root.put("verifiedEntitlement", JSONObject().put("state", "ACTIVE"))
        root.put("cloudUsage", JSONObject().put("remaining", 3))
        root.put("playPurchase", JSONObject().put("token", "synthetic-purchase"))
        root.put("evidence", JSONObject().put("owned.mp4", JSONObject().put("sha256", "local-only")))
        CloudSessionState.install(root, tokens("guardian-b"))
        assertEquals("CANCELLED", CloudOutbox.events(root).getJSONObject("local-event").getString("status"))
        assertFalse(root.has("verifiedEntitlement")); assertFalse(root.has("cloudUsage")); assertFalse(root.has("playPurchase"))
        assertTrue(root.getJSONObject("evidence").has("owned.mp4"))
    }
    @Test fun sameAccountLoginPreservesItsQueueAndRevokesStaleWorkerSession() {
        val root = JSONObject()
        CloudSessionState.install(root, tokens("guardian-a"))
        CloudOutbox.events(root).put("local-event", JSONObject().put("status", "QUEUED"))
        val before = JSONObject(CloudApi.config(root).toString())
        CloudSessionState.install(root, tokens("guardian-a"))
        assertEquals("QUEUED", CloudOutbox.events(root).getJSONObject("local-event").getString("status"))
        assertFalse(CloudSessionState.sameSession(CloudApi.config(root), before))
        assertTrue(CloudSessionState.sameSession(CloudApi.config(root), CloudApi.config(root)))
    }
    @Test fun logoutRevokesTokensAndKeepsLocalSafetyState() {
        val root = JSONObject()
        CloudSessionState.install(root, tokens("guardian-a"))
        root.put("state", JSONObject().put("phase", "SOS_ACTIVE"))
        root.put("verifiedEntitlement", JSONObject().put("state", "ACTIVE"))
        CloudOutbox.events(root).put("job", JSONObject().put("status", "UPLOADING"))
        val before = JSONObject(CloudApi.config(root).toString())
        CloudSessionState.logout(root)
        assertEquals("SOS_ACTIVE", root.getJSONObject("state").getString("phase"))
        assertEquals("CANCELLED", CloudOutbox.events(root).getJSONObject("job").getString("status"))
        assertFalse(CloudApi.config(root).has("idToken")); assertFalse(CloudApi.config(root).has("refreshToken"))
        assertFalse(root.has("verifiedEntitlement")); assertFalse(CloudSessionState.sameSession(CloudApi.config(root), before))
    }
    @Test fun closingEvidenceCannotRevivePendingCancellation() {
        val event = UUID.randomUUID().toString()
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "review-$event.mp4")
        val manifest = File(file.parentFile, "${event}_evidence_manifest.json")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("qa-sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            FamilyStore(context).update { root -> CloudOutbox.events(root).put(event, JSONObject().put("eventId", event).put("status", "CANCEL_PENDING")) }
            val result = EvidenceManager.finalized(context, file, event)
            assertTrue(EvidenceManager.verify(context, result))
            assertEquals("CANCEL_PENDING", CloudOutbox.events(FamilyStore(context).snapshot()).getJSONObject(event).getString("status"))
            CloudOutbox.retry(context)
            assertEquals("CANCEL_PENDING", CloudOutbox.events(FamilyStore(context).snapshot()).getJSONObject(event).getString("status"))
        } finally { file.delete(); manifest.delete() }
    }
}
