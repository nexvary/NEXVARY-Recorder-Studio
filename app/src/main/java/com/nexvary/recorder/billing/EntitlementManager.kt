package com.nexvary.recorder.billing

import android.content.Context
import com.nexvary.recorder.family.FamilyStore

enum class EntitlementState { FREE, PREMIUM, TRIAL, GRACE_PERIOD, ACCOUNT_HOLD, EXPIRED }

enum class SubscriptionState { UNVERIFIED, PENDING, ACTIVE, GRACE_PERIOD, CANCELLED_ACTIVE, ACCOUNT_HOLD, PAUSED, EXPIRED, REVOKED }
data class VerifiedEntitlement(val state: SubscriptionState,val expiresAt: Long,val verifiedAt: Long)
enum class SafetyFeature(val basic: Boolean) { RECORDING(true), ADVANCED_EVIDENCE(true), BASIC_SOS(true), BASIC_CHECK(true), PROFILE(true),
    MULTIPLE_GUARDIANS(false), JOURNEY(false), MOTION(false), CLOUD_UPLOAD(false), AUTOMATED_SMTP(false) }

object EntitlementPolicy {
    fun premium(value: VerifiedEntitlement?,now: Long): Boolean = value != null &&
        value.state in listOf(SubscriptionState.ACTIVE,SubscriptionState.GRACE_PERIOD,SubscriptionState.CANCELLED_ACTIVE) &&
        now >= value.verifiedAt && now < minOf(value.expiresAt,value.verifiedAt+24*60*60_000L)
}
/** Verified feature gates; local SOS and evidence remain available. */
class EntitlementManager(private val context: Context) {
    fun state(): EntitlementState {
        val verified=FamilyStore(context).snapshot().optJSONObject("verifiedEntitlement")
        if(verified?.optString("state")=="ACCOUNT_HOLD") return EntitlementState.ACCOUNT_HOLD
        if(hasPremium()) return if(verified?.optString("state")=="GRACE_PERIOD") EntitlementState.GRACE_PERIOD else EntitlementState.PREMIUM
        return if(verified!=null) EntitlementState.EXPIRED else EntitlementState.FREE
    }
    fun hasPremium(): Boolean {
        val value=FamilyStore(context).snapshot().optJSONObject("verifiedEntitlement") ?: return false
        val entitlement=runCatching { VerifiedEntitlement(SubscriptionState.valueOf(value.getString("state")),value.getLong("expiresAt"),value.getLong("verifiedAt")) }.getOrNull()
        return EntitlementPolicy.premium(entitlement,System.currentTimeMillis())
    }
    fun canUse(feature: SafetyFeature)=feature.basic || hasPremium()
    companion object { const val MONTHLY="guardian_premium_monthly";const val YEARLY="guardian_premium_yearly" }
}

/** Must validate with Google Play Developer API on an authenticated backend; never bundle a service key. */
interface PurchaseVerifier {
    fun verify(productIds: List<String>,purchaseToken: String): VerifiedEntitlement
}


class CloudPurchaseVerifier(private val context: Context): PurchaseVerifier {
    override fun verify(productIds: List<String>,purchaseToken: String): VerifiedEntitlement {
        val result=com.nexvary.recorder.guardian.CloudApi(context).request("POST","/billing/verify",org.json.JSONObject().put("purchaseToken",purchaseToken))
        val state=when(result.getString("state")) {
            "PREMIUM","TRIAL" -> SubscriptionState.ACTIVE
            "GRACE_PERIOD" -> SubscriptionState.GRACE_PERIOD
            "ACCOUNT_HOLD" -> SubscriptionState.ACCOUNT_HOLD
            else -> SubscriptionState.EXPIRED
        }
        return VerifiedEntitlement(state,result.getLong("expiresAt"),result.getLong("verifiedAt"))
    }
}
