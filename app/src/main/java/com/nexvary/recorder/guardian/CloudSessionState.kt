package com.nexvary.recorder.guardian

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Account switches revoke local transport authority, not local SOS or evidence. */
object CloudSessionState {
    fun binding(token: String): String {
        val payload = token.split('.').getOrNull(1) ?: throw CloudFailure("LOGIN_REQUIRED", 401)
        val sub = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)).getString("sub")
        require(sub.isNotBlank())
        return MessageDigest.getInstance("SHA-256").digest(sub.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun install(root: JSONObject, tokens: JSONObject) {
        val config = CloudApi.config(root)
        val owner = binding(tokens.getString("IdToken"))
        val oldOwner = config.optString("accountBinding").ifBlank { runCatching { binding(config.optString("idToken")) }.getOrDefault("") }
        if (oldOwner != owner) clearAccountData(root)
        config.put("idToken", tokens.getString("IdToken")).put("expiresAt", System.currentTimeMillis() + tokens.getLong("ExpiresIn") * 1000)
            .put("refreshToken", tokens.optString("RefreshToken")).put("accountBinding", owner).put("sessionNonce", UUID.randomUUID().toString())
    }
    fun clearAccountData(root: JSONObject) {
        root.remove("verifiedEntitlement"); root.remove("playPurchase"); root.remove("cloudUsage"); root.remove("cloudInvitation")
        val events = CloudOutbox.events(root)
        events.keys().forEach { key ->
            val row = events.getJSONObject(key)
            if (row.optString("status") !in listOf("SENT", "CANCELLED")) row.put("status", "CANCELLED").put("error", "ACCOUNT_SESSION_CHANGED")
        }
    }
    fun logout(root: JSONObject) {
        clearAccountData(root)
        CloudApi.config(root).apply { remove("idToken"); remove("expiresAt"); remove("refreshToken"); put("sessionNonce", UUID.randomUUID().toString()) }
    }
    fun sameSession(current: JSONObject, expected: JSONObject): Boolean =
        current.optString("baseUrl") == expected.optString("baseUrl") &&
            current.optString("sessionNonce") == expected.optString("sessionNonce") &&
            current.optString("accountBinding") == expected.optString("accountBinding") &&
            current.optString("idToken").isNotBlank()
}
