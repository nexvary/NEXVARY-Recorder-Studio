package com.nexvary.recorder.delivery

import android.content.Context
import com.nexvary.recorder.family.FamilyStore
import org.json.JSONObject
import java.security.MessageDigest

/** Only a classified result and an account fingerprint are retained; never exception text or credentials. */
object SmtpDiagnostics {
    private fun fingerprint(profile: SmtpProfile): String {
        val bytes=listOf(profile.host,profile.port.toString(),profile.directTls.toString(),profile.username,profile.password,profile.from).joinToString("\u0000").toByteArray(Charsets.UTF_8)
        return try { MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } } finally { bytes.fill(0) }
    }
    fun record(context: Context,profile: SmtpProfile,result: String) {
        require(result in SmtpFailure.entries.map { it.name }+"OK")
        FamilyStore(context).update { it.put("smtpDiagnostic",JSONObject().put("account",fingerprint(profile)).put("result",result).put("testedAt",System.currentTimeMillis()).put("tlsMode",if(profile.directTls) "TLS" else "STARTTLS")) }
    }
    fun last(context: Context,profile: SmtpProfile?): JSONObject? {
        if(profile==null) return null
        return FamilyStore(context).snapshot().optJSONObject("smtpDiagnostic")?.takeIf { it.optString("account")==fingerprint(profile) }
    }
}
