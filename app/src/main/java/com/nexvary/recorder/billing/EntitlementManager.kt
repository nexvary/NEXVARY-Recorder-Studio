package com.nexvary.recorder.billing

import android.content.Context
import com.nexvary.recorder.BuildConfig
import com.nexvary.recorder.family.FamilyStore

enum class SubscriptionState { UNVERIFIED, PENDING, ACTIVE, GRACE_PERIOD, CANCELLED_ACTIVE, ACCOUNT_HOLD, PAUSED, EXPIRED, REVOKED }
data class VerifiedEntitlement(val state: SubscriptionState,val expiresAt: Long,val verifiedAt: Long)
enum class SafetyFeature(val basic: Boolean) { RECORDING(true), BASIC_SOS(true), BASIC_CHECK(true), PROFILE(true),
    MULTIPLE_GUARDIANS(false), JOURNEY(false), ADVANCED_EVIDENCE(false), MOTION(false), CLOUD_UPLOAD(false), AUTOMATED_SMTP(false) }

object EntitlementPolicy {
    fun premium(value: VerifiedEntitlement?,now: Long): Boolean = value != null &&
        value.state in listOf(SubscriptionState.ACTIVE,SubscriptionState.GRACE_PERIOD,SubscriptionState.CANCELLED_ACTIVE) &&
        now >= value.verifiedAt && now < minOf(value.expiresAt,value.verifiedAt+24*60*60_000L)
}
/** No local switch grants premium. Debug preview access is separate from purchase entitlement. */
class EntitlementManager(private val context: Context) {
    fun hasPremium(): Boolean {
        val value=FamilyStore(context).snapshot().optJSONObject("verifiedEntitlement") ?: return false
        val entitlement=runCatching { VerifiedEntitlement(SubscriptionState.valueOf(value.getString("state")),value.getLong("expiresAt"),value.getLong("verifiedAt")) }.getOrNull()
        return EntitlementPolicy.premium(entitlement,System.currentTimeMillis())
    }
    fun canUse(feature: SafetyFeature)=feature.basic || hasPremium() || BuildConfig.DEBUG
    companion object { const val MONTHLY="recorder_premium_monthly";const val YEARLY="recorder_premium_yearly" }
}

/** Must validate with Google Play Developer API on an authenticated backend; never bundle a service key. */
interface PurchaseVerifier {
    fun verify(productIds: List<String>,purchaseToken: String): VerifiedEntitlement
}
