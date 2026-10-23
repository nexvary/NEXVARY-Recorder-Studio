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
    override val screenshotsAllowed = true
    override val title=R.string.guardian_cloud
    private lateinit var status: TextView
    private lateinit var events: LinearLayout
    private lateinit var accountForm: LinearLayout
    private var authUi: (Boolean) -> Unit = {}
    override fun build() {
        status=DeliveryUi.text(root,R.string.guardian_cloud_unconfigured)
        accountForm=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE }
        DeliveryUi.text(root,R.string.settings_account_note)
        val institution=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE }
        val institutionUrl=FamilyUi.field(institution,R.string.service_institution_address,R.id.guardianInstitutionUrl,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        fun connect(address: String, expected: String) {
            if(address.isBlank()) { status.setText(R.string.service_unavailable);return }
            val origin=runCatching { GuardianServiceAddress.normalize(address) }.getOrNull()
            if(origin==null) { status.setText(R.string.guardian_invalid_config);return }
            MaterialAlertDialogBuilder(this).setTitle(R.string.service_connect)
                .setMessage(getString(R.string.service_connect_confirm,origin)).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.service_connect) { _,_ ->
                    status.setText(R.string.service_connecting)
                    FamilyUi.io(this,{
                        runCatching {
                            val description=CloudApi.discover(origin)
                            require(description.getJSONObject("service").getString("edition")==expected)
                            FamilyStore(this).update { CloudApi.installService(it,description) }
                            description
                        }
                    }) { result ->
                        if(result.isSuccess) {
                            val service=result.getOrThrow().getJSONObject("service")
                            status.text=getString(R.string.service_connected,service.getString("name"))
                            if(!service.optBoolean("remoteSos") || !service.optBoolean("evidenceUpload")) status.append("\n"+getString(R.string.service_remote_off))
                            accountForm.visibility=android.view.View.VISIBLE;authUi(false)
                        } else status.setText(R.string.service_connect_failed)
                    }
                }.show()
        }
        com.nexvary.recorder.ui.FeatureCards.grid(root,listOf(
            com.nexvary.recorder.ui.FeatureCards.Item(R.id.guardianPersonalService,R.drawable.asset_family,R.string.service_personal,R.string.service_personal_note) {
                connect(com.nexvary.recorder.BuildConfig.GUARDIAN_PUBLIC_API_URL,"MANAGED")
            },
            com.nexvary.recorder.ui.FeatureCards.Item(R.id.guardianInstitutionService,R.drawable.asset_tools,R.string.service_institution,R.string.service_institution_note) {
                institution.visibility=android.view.View.VISIBLE
                institutionUrl.requestFocus()
            }
        ))
        DeliveryUi.button(institution,R.string.service_connect,R.id.guardianConnectInstitution) { connect(institutionUrl.text.toString(),"INSTITUTION") }
        root.addView(institution)
        val connectionTools=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val advanced=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE }
        DeliveryUi.button(connectionTools,R.string.service_advanced,R.id.guardianAdvancedConnection) {
            advanced.visibility=if(advanced.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }
        DeliveryUi.text(advanced,R.string.service_legacy_note)
        val base=FamilyUi.field(advanced,R.string.guardian_api,R.id.guardianApi,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val region=FamilyUi.field(advanced,R.string.guardian_region,R.id.guardianRegion)
        val client=FamilyUi.field(advanced,R.string.guardian_client,R.id.guardianClient)
        FamilyUi.io(this,{ CloudApi.config(this) }) { data ->
            if(CloudApi.portable(data)) institutionUrl.setText(data.optString("baseUrl"))
            else { base.setText(data.optString("baseUrl"));region.setText(data.optString("region"));client.setText(data.optString("clientId")) }
            accountForm.visibility=if(data.optString("baseUrl").isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            data.optJSONObject("service")?.let { service ->
                status.text=getString(R.string.service_connected,service.optString("name"))
                if(!service.optBoolean("remoteSos") || !service.optBoolean("evidenceUpload")) status.append("\n"+getString(R.string.service_remote_off))
            }
        }
        DeliveryUi.button(advanced,R.string.guardian_save_settings) {
            val address=runCatching { GuardianServiceAddress.normalize(base.text.toString()) }.getOrNull()
            val area=region.text.toString().trim();val id=client.text.toString().trim()
            if(address==null || !area.matches(Regex("[a-z]{2}-[a-z]+-\\d")) || !id.matches(Regex("[a-zA-Z0-9]{10,128}"))) { status.setText(R.string.guardian_invalid_config);return@button }
            FamilyUi.io(this,{ FamilyStore(this).update { CloudSessionState.logout(it);it.put("cloud",JSONObject().put("baseUrl",address).put("region",area).put("clientId",id)) } }) {
                status.setText(R.string.guardian_settings_saved);accountForm.visibility=android.view.View.VISIBLE;authUi(false)
            }
        }
        connectionTools.addView(advanced)
        root.addView(accountForm)
        val credentials=DeliveryUi.section(accountForm,R.string.guardian_login)
        val email=FamilyUi.field(credentials,R.string.guardian_email,R.id.guardianEmail,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=FamilyUi.field(credentials,R.string.smtp_password,R.id.guardianPassword,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val code=FamilyUi.field(credentials,R.string.guardian_code,R.id.guardianCode,type=InputType.TYPE_CLASS_NUMBER)
        val manage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE;accountForm.addView(this) }
        var mode="LOGIN"
        lateinit var submit: androidx.appcompat.widget.AppCompatButton
        lateinit var signup: androidx.appcompat.widget.AppCompatButton
        lateinit var forgot: androidx.appcompat.widget.AppCompatButton
        lateinit var login: androidx.appcompat.widget.AppCompatButton
        lateinit var confirm: androidx.appcompat.widget.AppCompatButton
        fun visible(view: android.view.View,show: Boolean) { view.visibility=if(show) android.view.View.VISIBLE else android.view.View.GONE }
        fun fieldVisible(view: EditText,show: Boolean) {
            visible(view,show);val parent=view.parent as LinearLayout;visible(parent.getChildAt(parent.indexOfChild(view)-1),show)
        }
        fun renderAuth(signedIn: Boolean=false) {
            visible(credentials,!signedIn);visible(manage,signedIn)
            fieldVisible(password,mode in listOf("LOGIN","SIGNUP","RESET"));fieldVisible(code,mode in listOf("CONFIRM","RESET"))
            submit.setText(when(mode) { "SIGNUP" -> R.string.guardian_signup;"CONFIRM" -> R.string.guardian_confirm;"FORGOT" -> R.string.guardian_forgot;"RESET" -> R.string.guardian_reset_password;else -> R.string.guardian_login })
            (credentials.getChildAt(0) as TextView).text=submit.text
            visible(signup,mode=="LOGIN");visible(forgot,mode=="LOGIN");visible(login,mode!="LOGIN");visible(confirm,mode in listOf("LOGIN","SIGNUP"))
        }
        submit=DeliveryUi.button(credentials,R.string.guardian_login,R.id.guardianAuthSubmit) {
            val action=mode;val e=email.text.toString();val p=password.text.toString();val c=code.text.toString();password.text.clear();submit.isEnabled=false
            FamilyUi.io(this,{ runCatching { when(action) {
                "SIGNUP" -> CloudApi(this).signup(e,p)
                "CONFIRM" -> CloudApi(this).confirm(e,c)
                "FORGOT" -> CloudApi(this).forgot(e)
                "RESET" -> CloudApi(this).reset(e,c,p)
                else -> CloudApi(this).authenticate(e,p)
            } } }) { result ->
                submit.isEnabled=true;status.setText(if(result.isSuccess) R.string.guardian_auth_ok else R.string.guardian_auth_failed)
                if(result.isSuccess) {
                    mode=when(action) { "SIGNUP" -> "CONFIRM";"FORGOT" -> "RESET";else -> "LOGIN" }
                    code.text.clear()
                }
                val signedIn=CloudApi.config(this).optString("idToken").isNotBlank();renderAuth(signedIn)
                if(result.isSuccess && signedIn) { CloudOutbox.retry(this);refresh() }
            }
        }
        signup=DeliveryUi.button(credentials,R.string.guardian_signup) { mode="SIGNUP";renderAuth() }
        forgot=DeliveryUi.button(credentials,R.string.guardian_forgot) { mode="FORGOT";renderAuth() }
        login=DeliveryUi.button(credentials,R.string.guardian_login) { mode="LOGIN";renderAuth() }
        confirm=DeliveryUi.button(credentials,R.string.guardian_confirm) { mode="CONFIRM";renderAuth() }
        authUi={ renderAuth(it) }
        renderAuth()
        FamilyUi.io(this,{ CloudApi.config(this).optString("idToken").isNotBlank() }) { renderAuth(it) }
        DeliveryUi.button(manage,R.string.guardian_usage) { refresh() }
        DeliveryUi.button(manage,R.string.mail_retry) { FamilyUi.io(this,{ CloudOutbox.retry(this,manual=true) }) { refresh() } }
        val contacts=com.nexvary.recorder.ui.SettingsSections.add(manage,R.string.fs_contacts)
        val contactEmail=FamilyUi.field(contacts,R.string.fs_email,R.id.guardianContactEmail,type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val contactCode=FamilyUi.field(contacts,R.string.guardian_code,R.id.guardianContactCode,type=InputType.TYPE_CLASS_NUMBER)
        fun contactAction(action: () -> Unit) { FamilyUi.io(this,{ runCatching(action) }) { status.setText(if(it.isSuccess) R.string.guardian_auth_ok else R.string.guardian_auth_failed) } }
        DeliveryUi.button(contacts,R.string.guardian_invite) { val e=contactEmail.text.toString();contactAction { val result=CloudApi(this).request("POST","/contacts/invite",JSONObject().put("email",e));FamilyStore(this).update { it.put("cloudInvitation",result.getString("invitation")) } } }
        DeliveryUi.button(contacts,R.string.guardian_confirm_contact) { val c=contactCode.text.toString();contactAction { val id=FamilyStore(this).snapshot().optString("cloudInvitation");CloudApi(this).request("POST","/contacts/confirm",JSONObject().put("invitation",id).put("code",c)) } }
        DeliveryUi.button(manage,R.string.guardian_logout) { FamilyUi.io(this,{ CloudApi(this).logout() }) { mode="LOGIN";renderAuth();status.setText(R.string.guardian_login_required) } }
        val deletion=com.nexvary.recorder.ui.SettingsSections.add(manage,R.string.guardian_delete_account)
        DeliveryUi.button(deletion,R.string.guardian_delete_account,red=true) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.guardian_delete_note).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.guardian_delete_account) { _,_ ->
                FamilyUi.io(this,{ runCatching { CloudApi(this).request("DELETE","/account");FamilyStore(this).update { it.remove("cloud");it.remove("cloudUsage");it.remove("cloudEvents");it.remove("verifiedEntitlement") } } }) { result ->
                    status.setText(if(result.isSuccess) R.string.guardian_deleted else R.string.guardian_auth_failed)
                    if(result.isSuccess) { renderAuth();accountForm.visibility=android.view.View.GONE }
                }
            }.show()
        }
        root.addView(connectionTools)
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
            CloudApi.config(snapshot).optJSONObject("service")?.let { service ->
                if(!service.optBoolean("remoteSos") || !service.optBoolean("evidenceUpload")) status.append("\n"+getString(R.string.service_remote_off))
                if(service.optString("serviceState")=="DEGRADED") status.append("\n"+getString(R.string.service_busy))
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
