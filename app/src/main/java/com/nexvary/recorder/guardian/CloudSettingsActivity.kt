package com.nexvary.recorder.guardian

import android.text.InputType
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CloudSettingsActivity: ProtectedFamilyPage() {
    override val title=R.string.guardian_cloud
    private lateinit var status: TextView
    private lateinit var events: LinearLayout
    override fun build() {
        status=DeliveryUi.text(root,R.string.guardian_cloud_unconfigured)
        DeliveryUi.text(root,R.string.guardian_cloud_note)
        val base=FamilyUi.field(root,R.string.guardian_api,R.id.guardianApi,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val region=FamilyUi.field(root,R.string.guardian_region,R.id.guardianRegion)
        val client=FamilyUi.field(root,R.string.guardian_client,R.id.guardianClient)
        FamilyUi.io(this,{ CloudApi.config(this) }) { data -> base.setText(data.optString("baseUrl"));region.setText(data.optString("region"));client.setText(data.optString("clientId")) }
        DeliveryUi.button(root,R.string.guardian_save_settings) {
            val address=base.text.toString().trim().trimEnd('/');val area=region.text.toString().trim();val id=client.text.toString().trim()
            val url=runCatching { java.net.URI(address) }.getOrNull()
            if(url?.scheme!="https" || url.host.isNullOrBlank() || url.userInfo!=null || !url.path.isNullOrEmpty() || url.query!=null || url.fragment!=null || !area.matches(Regex("[a-z]{2}-[a-z]+-\\d")) || !id.matches(Regex("[a-zA-Z0-9]{10,128}"))) { status.setText(R.string.guardian_invalid_config);return@button }
            FamilyUi.io(this,{ FamilyStore(this).update { it.put("cloud",JSONObject().put("baseUrl",address).put("region",area).put("clientId",id)) } }) { status.setText(R.string.guardian_settings_saved) }
        }
        val email=FamilyUi.field(root,R.string.guardian_email,R.id.guardianEmail,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=FamilyUi.field(root,R.string.smtp_password,R.id.guardianPassword,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val code=FamilyUi.field(root,R.string.guardian_code,R.id.guardianCode,type=InputType.TYPE_CLASS_NUMBER)
        fun auth(action: () -> Unit) { FamilyUi.io(this,{ runCatching(action) }) { result -> password.text.clear();status.setText(if(result.isSuccess) R.string.guardian_auth_ok else R.string.guardian_auth_failed);if(result.isSuccess) { CloudOutbox.retry(this);refresh() } } }
        DeliveryUi.button(root,R.string.guardian_signup) { val e=email.text.toString();val p=password.text.toString();password.text.clear();auth { CloudApi(this).signup(e,p) } }
        DeliveryUi.button(root,R.string.guardian_confirm) { val e=email.text.toString();val c=code.text.toString();auth { CloudApi(this).confirm(e,c) } }
        DeliveryUi.button(root,R.string.guardian_login) { val e=email.text.toString();val p=password.text.toString();password.text.clear();auth { CloudApi(this).authenticate(e,p) } }
        DeliveryUi.button(root,R.string.guardian_forgot) { val e=email.text.toString();auth { CloudApi(this).forgot(e) } }
        DeliveryUi.button(root,R.string.guardian_reset_password) { val e=email.text.toString();val c=code.text.toString();val p=password.text.toString();password.text.clear();auth { CloudApi(this).reset(e,c,p) } }
        DeliveryUi.button(root,R.string.guardian_invite) { val e=email.text.toString();auth { val result=CloudApi(this).request("POST","/contacts/invite",JSONObject().put("email",e));FamilyStore(this).update { it.put("cloudInvitation",result.getString("invitation")) } } }
        DeliveryUi.button(root,R.string.guardian_confirm_contact) { val c=code.text.toString();auth { val id=FamilyStore(this).snapshot().optString("cloudInvitation");CloudApi(this).request("POST","/contacts/confirm",JSONObject().put("invitation",id).put("code",c)) } }
        DeliveryUi.button(root,R.string.guardian_usage) { refresh() }
        DeliveryUi.button(root,R.string.mail_retry) { FamilyUi.io(this,{ CloudOutbox.retry(this) }) { refresh() } }
        DeliveryUi.button(root,R.string.guardian_logout) { FamilyUi.io(this,{ FamilyStore(this).update { CloudApi.config(it).remove("idToken");CloudApi.config(it).remove("expiresAt");CloudApi.config(it).remove("refreshToken") } }) { status.setText(R.string.guardian_login_required) } }
        DeliveryUi.button(root,R.string.guardian_delete_account,red=true) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.guardian_delete_note).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.guardian_delete_account) { _,_ ->
                FamilyUi.io(this,{ runCatching { CloudApi(this).request("DELETE","/account");FamilyStore(this).update { it.remove("cloud");it.remove("cloudUsage");it.remove("cloudEvents");it.remove("verifiedEntitlement") } } }) { result -> status.setText(if(result.isSuccess) R.string.guardian_deleted else R.string.guardian_auth_failed) }
            }.show()
        }
        events=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;root.addView(this) }
        renderCached()
    }
    private fun refresh() {
        FamilyUi.io(this,{ runCatching { QuotaManager(this).refresh() } }) { result ->
            if(result.isFailure) status.setText(R.string.guardian_login_required)
            renderCached()
        }
    }
    private fun renderCached() {
        FamilyUi.io(this,{ FamilyStore(this).snapshot() }) { snapshot ->
            snapshot.optJSONObject("cloudUsage")?.let { usage ->
                val reset=Instant.ofEpochSecond(usage.optLong("resetAt")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                status.text=getString(R.string.guardian_usage_format,usage.optInt("sosRemaining"),usage.optInt("monthlyLimit"),usage.optInt("uploadsRemaining"),usage.optInt("monthlyLimit"),reset)
                if(usage.optInt("remaining")==0) status.append("\n"+getString(R.string.guardian_quota_used))
            }
            events.removeAllViews()
            val jobs=CloudOutbox.events(snapshot)
            jobs.keys().asSequence().map { jobs.getJSONObject(it) }.sortedByDescending { it.optLong("createdAt") }.take(50).forEach { job ->
                val card=DeliveryUi.section(events,R.string.fs_alert)
                card.addView(TextView(this).apply { text=job.optString("eventId")+"\n"+job.optString("status") })
                if(job.optString("serverEventId").isNotBlank()) DeliveryUi.button(card,R.string.guardian_delete_evidence,red=true) {
                    val id=job.getString("serverEventId")
                    MaterialAlertDialogBuilder(this).setMessage(R.string.guardian_delete_note).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.guardian_delete_evidence) { _,_ ->
                        FamilyUi.io(this,{ runCatching { CloudApi(this).request("DELETE","/events/$id");FamilyStore(this).update { CloudOutbox.events(it).getJSONObject(job.getString("eventId")).put("status","CANCELLED") } } }) { renderCached() }
                    }.show()
                }
            }
        }
    }
}
