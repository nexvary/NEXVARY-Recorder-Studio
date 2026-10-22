package com.nexvary.recorder.family

import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
import com.nexvary.recorder.ui.*
import org.json.JSONObject

class FamilySettingsActivity: ProtectedFamilyPage() {
    override val screenshotsAllowed = true
    private val section get()=intent.getStringExtra("section").orEmpty()
    override val title get()=when(section) {
        "sharing" -> R.string.settings_sharing
        "evidence" -> R.string.settings_evidence
        "journey" -> R.string.fs_destination
        "device" -> R.string.settings_device
        else -> R.string.settings_safety
    }
    override fun build() {
        if(section !in listOf("sharing","evidence","journey","device")) { hub();return }
        FamilyUi.io(this,{ FamilyStore(this).snapshot() }) { data -> editor(data) }
    }
    private fun hub() {
        DeliveryUi.text(root,R.string.settings_safety_note)
        fun page(type: Class<out android.app.Activity>)=startActivity(Intent(this,type))
        fun section(name: String)=startActivity(Intent(this,FamilySettingsActivity::class.java).putExtra("section",name))
        FeatureCards.add(root,R.drawable.asset_family,R.string.fs_contacts) { page(TrustedContactsActivity::class.java) }
        FeatureCards.add(root,R.drawable.asset_sos,R.string.settings_sharing) { section("sharing") }
        FeatureCards.add(root,R.drawable.asset_info,R.string.fs_profile) { page(EmergencyProfileActivity::class.java) }
        FeatureCards.add(root,R.drawable.asset_settings,R.string.settings_pin) { page(ParentSettingsActivity::class.java) }
        FeatureCards.add(root,R.drawable.asset_family,R.string.guardian_child_mode) { com.nexvary.recorder.guardian.ChildMode.activate(this) }
        val optional=SettingsSections.add(root,R.string.settings_advanced,R.id.settingsAdvanced)
        FeatureCards.add(optional,R.drawable.asset_camera,R.string.settings_evidence) { section("evidence") }
        FeatureCards.add(optional,R.drawable.asset_family,R.string.fs_destination) { section("journey") }
        FeatureCards.add(optional,R.drawable.asset_settings,R.string.settings_device) { section("device") }
        FeatureCards.add(optional,R.drawable.asset_library,R.string.fs_history) { page(SafetyHistoryActivity::class.java) }
        FeatureCards.add(optional,R.drawable.asset_info,R.string.fs_privacy) { page(PrivacyCenterActivity::class.java) }
        FeatureCards.add(optional,R.drawable.asset_launcher,R.string.fs_subscriptions) { page(com.nexvary.recorder.billing.SubscriptionActivity::class.java) }
    }
    private fun editor(data: JSONObject) {
        val config=FamilyStore.config(data)
        val edits=mutableListOf<(JSONObject)->Unit>()
        var validate: () -> Boolean={ true }
        fun text(key: String,label: Int,id: Int=View.generateViewId()): EditText {
            val field=FamilyUi.field(root,label,id,config.optString(key))
            edits.add { it.put(key,field.text.toString().take(if(key=="note") 2000 else 120)) };return field
        }
        fun check(key: String,label: Int,id: Int=View.NO_ID): androidx.appcompat.widget.SwitchCompat {
            val field=FamilyUi.check(root,label,config.optBoolean(key),id)
            edits.add { it.put(key,field.isChecked) };return field
        }
        when(section) {
            "sharing" -> {
                DeliveryUi.text(root,R.string.settings_sharing_note)
                text("displayName",R.string.fs_display_name,R.id.fsDisplayName)
                text("note",R.string.fs_note,R.id.fsNote)
                check("autoShare",R.string.fs_auto_share_consent,R.id.fsAutoShare)
                check("location",R.string.fs_location_consent,R.id.fsLocation)
                check("shareDevice",R.string.fs_share_device)
                FeatureCards.add(root,R.drawable.asset_library,R.string.settings_account) { startActivity(Intent(this,com.nexvary.recorder.guardian.CloudSettingsActivity::class.java)) }
            }
            "evidence" -> {
                DeliveryUi.text(root,R.string.settings_evidence_note)
                DeliveryUi.text(root,R.string.fs_evidence_mode)
                val modes=listOf("NONE","AUDIO","VIDEO","PHOTO")
                val evidence=StudioSpinner.add(root,resources.getStringArray(R.array.fs_evidence_modes).toList(),R.id.fsEvidenceMode,modes.indexOf(config.optString("evidence","NONE")).coerceAtLeast(0))
                edits.add { it.put("evidence",modes[evidence.selectedItemPosition]) }
                check("videoMic",R.string.fs_video_mic)
                val auto=check("autoEvidence",R.string.fs_auto_evidence)
                validate={ !auto.isChecked || com.nexvary.recorder.billing.EntitlementManager(this).canUse(com.nexvary.recorder.billing.SafetyFeature.ADVANCED_EVIDENCE).also { if(!it) Toast.makeText(this,R.string.fs_billing_verifier_missing,Toast.LENGTH_LONG).show() } }
            }
            "device" -> {
                DeliveryUi.text(root,R.string.settings_device_note)
                check("immediate",R.string.fs_immediate_consent)
                check("audible",R.string.fs_audible)
                check("batteryAlerts",R.string.fs_battery_alerts)
                check("batterySaver",R.string.fs_battery_saver)
                val motion=check("motion",R.string.fs_motion_consent)
                DeliveryUi.text(root,R.string.fs_motion_note)
                val sensitivity=StudioSpinner.add(root,resources.getStringArray(R.array.fs_sensitivities).toList(),selected=config.optInt("sensitivity",1).coerceIn(0,2))
                edits.add { it.put("sensitivity",sensitivity.selectedItemPosition) }
                check("volumeTrigger",R.string.fs_volume_trigger)
                validate={
                    when {
                        motion.isChecked && !config.optBoolean("location") -> { Toast.makeText(this,R.string.fs_location_consent,Toast.LENGTH_LONG).show();false }
                        motion.isChecked && !com.nexvary.recorder.billing.EntitlementManager(this).canUse(com.nexvary.recorder.billing.SafetyFeature.MOTION) -> { Toast.makeText(this,R.string.fs_billing_verifier_missing,Toast.LENGTH_LONG).show();false }
                        else -> true
                    }
                }
            }
            "journey" -> {
                DeliveryUi.text(root,R.string.fs_journey_note)
                val dest=config.optJSONObject("destination") ?: JSONObject()
                val name=FamilyUi.field(root,R.string.fs_destination_name,R.id.fsDestination,dest.optString("name"))
                val lat=FamilyUi.field(root,R.string.fs_latitude,R.id.fsLatitude,dest.optString("latitude"),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                val lon=FamilyUi.field(root,R.string.fs_longitude,R.id.fsLongitude,dest.optString("longitude"),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                DeliveryUi.text(root,R.string.fs_journey_guardian)
                val guardians=FamilyStore.contacts(data).let { c -> (0 until c.length()).map { c.getJSONObject(it) } }
                val guardian=StudioSpinner.add(root,if(guardians.isEmpty()) listOf(getString(R.string.fs_none)) else guardians.map { it.optString("name") },selected=guardians.indexOfFirst { it.optString("id")==dest.optString("contactId") }.coerceAtLeast(0))
                fun coordinate(field: EditText)=DeliveryPolicy.digits(field.text.toString()).replace('٫','.').toDoubleOrNull()
                validate={
                    val a=coordinate(lat);val b=coordinate(lon)
                    if(name.text.isNotBlank() && (a==null || b==null || a !in -90.0..90.0 || b !in -180.0..180.0)) { lat.error=getString(R.string.fs_coordinates_invalid);false } else true
                }
                edits.add { value ->
                    if(name.text.isBlank()) value.remove("destination")
                    else value.put("destination",JSONObject().put("name",name.text.toString().take(120)).put("latitude",coordinate(lat)).put("longitude",coordinate(lon)).put("contactId",guardians.getOrNull(guardian.selectedItemPosition)?.optString("id").orEmpty()))
                }
            }
        }
        DeliveryUi.button(root,R.string.rescue_save,R.id.fsSaveSettings) {
            if(!validate()) return@button
            // Capture on the UI thread, merge only this section into the latest stored configuration.
            val patch=JSONObject();edits.forEach { it(patch) }
            MaterialAlertDialogBuilder(this).setTitle(R.string.fs_consent_review)
                .setMessage(getString(if(patch.optBoolean("autoShare",config.optBoolean("autoShare"))) R.string.fs_sharing_review else R.string.fs_local_only))
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.rescue_save) { _,_ ->
                    FamilyUi.io(this,{
                        FamilyStore(this).update { state ->
                            val current=FamilyStore.config(state)
                            patch.keys().forEach { current.put(it,patch.get(it)) }
                            if(section=="journey" && !patch.has("destination")) current.remove("destination")
                            state.put("config",current)
                            if(!current.optBoolean("autoShare")) FamilyStore.jobs(state).let { jobs -> jobs.keys().forEach { id -> jobs.getJSONObject(id).let { if(!it.optBoolean("manual") && it.optString("status") !in listOf("SENT","SENDING")) it.put("status","CANCELLED") } } }
                            FamilyStore.event(state,"CONFIGURATION_UPDATED")
                        }
                        stopService(Intent(this,SafetyLocationService::class.java))
                    }) { Toast.makeText(this,R.string.smtp_saved,Toast.LENGTH_LONG).show();finish() }
                }.show()
        }
    }
}

class TrustedContactsActivity: ProtectedFamilyPage() {
    override val title=R.string.fs_contacts
    private lateinit var rows: LinearLayout
    override fun build() {
        DeliveryUi.text(root,R.string.fs_contacts_note)
        DeliveryUi.button(root,R.string.rescue_add,R.id.fsAddContact) { edit(null) }
        DeliveryUi.button(root,R.string.fs_import_rescue) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.fs_import_note).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.rescue_continue) { _,_ -> FamilyUi.io(this,{
                    val repository=TrustedContactsRepository(this);val old=RescueCardsStore(this).read();var count=0
                    for(i in 0 until old.length()) {
                        val item=old.getJSONObject(i);val phone=com.nexvary.recorder.safety.RescuePolicy.normalizePhone(item.optString("phone"))
                        if(repository.all().none { it.optString("phone")==phone } && com.nexvary.recorder.safety.RescuePolicy.validPhone(phone)) {
                            repository.save(JSONObject().put("name",item.optString("name")).put("phone",phone).put("channel","SMS").put("role","EMERGENCY").put("relationship",""));count++
                        }
                    };count
                }) { count -> Toast.makeText(this,getString(R.string.fs_import_count,count),Toast.LENGTH_LONG).show();render() } }.show()
        }
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;root.addView(this) };render()
    }
    private fun render() {
        FamilyUi.io(this,{ TrustedContactsRepository(this).all() }) { contacts ->
            rows.removeAllViews()
            contacts.forEach { contact ->
                val card=DeliveryUi.section(rows,R.string.fs_contact)
                card.addView(TextView(this).apply { text=contact.optString("name");textSize=20f })
                card.addView(TextView(this).apply { text=listOf(contact.optString("phone"),contact.optString("email"),contact.optString("relationship")).filter { it.isNotBlank() }.joinToString("\n");textDirection=View.TEXT_DIRECTION_LTR })
                DeliveryUi.button(card,R.string.rescue_edit) { edit(contact) }
                DeliveryUi.button(card,R.string.rescue_delete,red=true) {
                    MaterialAlertDialogBuilder(this).setMessage(R.string.rescue_delete_confirm).setNegativeButton(R.string.cancel,null)
                        .setPositiveButton(R.string.rescue_delete) { _,_ -> FamilyUi.io(this,{ TrustedContactsRepository(this).delete(contact.getString("id")) }) { render() } }.show()
                }
            }
        }
    }
    private fun edit(original: JSONObject?) {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16) }
        val name=FamilyUi.field(form,R.string.rescue_name,R.id.fsContactName,original?.optString("name").orEmpty())
        val phone=FamilyUi.field(form,R.string.rescue_phone,R.id.fsContactPhone,original?.optString("phone").orEmpty(),InputType.TYPE_CLASS_PHONE)
        val email=FamilyUi.field(form,R.string.fs_email,R.id.fsContactEmail,original?.optString("email").orEmpty(),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val relation=FamilyUi.field(form,R.string.fs_relationship,R.id.fsRelationship,original?.optString("relationship").orEmpty())
        DeliveryUi.text(form,R.string.fs_guardian_role)
        val role=StudioSpinner.add(form,resources.getStringArray(R.array.fs_roles).toList(),selected=listOf("PRIMARY","SECONDARY","EMERGENCY").indexOf(original?.optString("role")).coerceAtLeast(0))
        DeliveryUi.text(form,R.string.fs_channel)
        val channel=StudioSpinner.add(form,resources.getStringArray(R.array.fs_channels).toList(),selected=if(original?.optString("channel")=="SMS") 1 else 0)
        val dialog=MaterialAlertDialogBuilder(this).setTitle(R.string.fs_contact).setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.rescue_save,null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val selected=if(channel.selectedItemPosition==0) "EMAIL" else "SMS"
                val value=JSONObject().put("name",name.text.toString().trim()).put("phone",com.nexvary.recorder.safety.RescuePolicy.normalizePhone(phone.text.toString().trim()))
                    .put("email",email.text.toString().trim()).put("relationship",relation.text.toString().take(120))
                    .put("role",listOf("PRIMARY","SECONDARY","EMERGENCY")[role.selectedItemPosition]).put("channel",selected)
                original?.optString("id")?.let { value.put("id",it) }
                if(name.text.isBlank() || (selected=="EMAIL" && !DeliveryPolicy.email(value.optString("email"))) ||
                    (selected=="SMS" && !com.nexvary.recorder.safety.RescuePolicy.validPhone(value.optString("phone")))) { name.error=getString(R.string.fs_contact_invalid);return@setOnClickListener }
                FamilyUi.io(this,{ TrustedContactsRepository(this).save(value) }) { dialog.dismiss();render() }
            }
        };dialog.show()
    }
}

class EmergencyProfileActivity: ProtectedFamilyPage() {
    override val title=R.string.fs_profile
    override fun build() {
        FamilyUi.io(this,{ FamilyStore(this).snapshot() }) { data ->
            DeliveryUi.text(root,R.string.fs_profile_note)
            val profile=data.optJSONObject("medical") ?: JSONObject()
            val fields=listOf("name" to R.string.fs_full_name,"age" to R.string.fs_age,"blood" to R.string.fs_blood,
                "allergies" to R.string.fs_allergies,"medications" to R.string.fs_medications,"notes" to R.string.fs_medical_notes,
                "guardian" to R.string.fs_guardian,"phone" to R.string.fs_emergency_phone,"secondary" to R.string.fs_secondary_phone)
            val editors=fields.map { (key,label) -> key to FamilyUi.field(root,label,View.generateViewId(),profile.optString(key),if(key in listOf("phone","secondary")) InputType.TYPE_CLASS_PHONE else if(key=="age") InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT) }
            val show=FamilyUi.check(root,R.string.fs_show_medical,FamilyStore.config(data).optBoolean("showMedical"))
            val share=FamilyUi.check(root,R.string.fs_share_medical,FamilyStore.config(data).optBoolean("shareMedical"))
            DeliveryUi.button(root,R.string.rescue_save,R.id.fsSaveProfile) {
                val value=JSONObject();editors.forEach { (key,field) -> value.put(key,field.text.toString().take(1000)) }
                val showValue=show.isChecked;val shareValue=share.isChecked
                FamilyUi.io(this,{ FamilyStore(this).update { state ->
                    state.put("medical",value);val cfg=FamilyStore.config(state);cfg.put("showMedical",showValue).put("shareMedical",shareValue);state.put("config",cfg)
                    FamilyStore.event(state,"EMERGENCY_PROFILE_UPDATED")
                } }) { Toast.makeText(this,R.string.smtp_saved,Toast.LENGTH_LONG).show() }
            }
        }
    }
}

class ParentSettingsActivity: ProtectedFamilyPage() {
    override val title=R.string.settings_pin
    override fun build() {
        DeliveryUi.text(root,R.string.fs_parent_gate_note)
        val pin=FamilyUi.field(root,R.string.fs_parent_pin,R.id.fsNewPin,type=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).apply { isSaveEnabled=false }
        val confirmation=FamilyUi.field(root,R.string.vault_confirm_pin,R.id.fsConfirmPin,type=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).apply { isSaveEnabled=false }
        DeliveryUi.button(root,R.string.rescue_save,R.id.fsSavePin) {
            val value=DeliveryPolicy.digits(pin.text.toString())
            if(value.length !in 6..12 || value.any { it !in '0'..'9' } || value!=DeliveryPolicy.digits(confirmation.text.toString())) { pin.error=getString(R.string.vault_pin_invalid);return@button }
            pin.text.clear();confirmation.text.clear()
            FamilyUi.io(this,{ GuardianGate.create(this,value) }) { Toast.makeText(this,R.string.smtp_saved,Toast.LENGTH_LONG).show() }
        }
        DeliveryUi.button(root,R.string.fs_parent_lock) { GuardianGate.lock();finish() }
        DeliveryUi.button(root,R.string.fs_parent_disable,red=true) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.fs_parent_disable_note).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.fs_parent_disable) { _,_ -> FamilyUi.io(this,{ FamilyStore(this).update { it.remove("guardianPin") };GuardianGate.lock() }) { finish() } }.show()
        }
    }
}
