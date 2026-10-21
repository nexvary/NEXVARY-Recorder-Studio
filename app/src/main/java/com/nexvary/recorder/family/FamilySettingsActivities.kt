package com.nexvary.recorder.family

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
import org.json.JSONObject

class FamilySettingsActivity: ProtectedFamilyPage() {
    override val title=R.string.fs_settings
    override fun build() {
        FamilyUi.io(this,{ FamilyStore(this).snapshot() }) { data ->
            val config=FamilyStore.config(data)
            DeliveryUi.text(root,R.string.fs_settings_note)
            val name=FamilyUi.field(root,R.string.fs_display_name,R.id.fsDisplayName,config.optString("displayName"))
            val note=FamilyUi.field(root,R.string.fs_note,R.id.fsNote,config.optString("note"))
            val share=FamilyUi.check(root,R.string.fs_auto_share_consent,config.optBoolean("autoShare"),R.id.fsAutoShare)
            val location=FamilyUi.check(root,R.string.fs_location_consent,config.optBoolean("location"),R.id.fsLocation)
            val immediate=FamilyUi.check(root,R.string.fs_immediate_consent,config.optBoolean("immediate"))
            val audible=FamilyUi.check(root,R.string.fs_audible,config.optBoolean("audible"))
            val device=FamilyUi.check(root,R.string.fs_share_device,config.optBoolean("shareDevice"))
            DeliveryUi.text(root,R.string.fs_monitor_note)
            val battery=FamilyUi.check(root,R.string.fs_battery_alerts,config.optBoolean("batteryAlerts"))
            val saver=FamilyUi.check(root,R.string.fs_battery_saver,config.optBoolean("batterySaver"))
            val motion=FamilyUi.check(root,R.string.fs_motion_consent,config.optBoolean("motion"))
            DeliveryUi.text(root,R.string.fs_motion_note)
            val sensitivity=Spinner(this).apply { adapter=ArrayAdapter(this@FamilySettingsActivity,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.fs_sensitivities));setSelection(config.optInt("sensitivity",1).coerceIn(0,2));root.addView(this) }
            val volume=FamilyUi.check(root,R.string.fs_volume_trigger,config.optBoolean("volumeTrigger"))
            DeliveryUi.text(root,R.string.fs_evidence_mode)
            val evidence=Spinner(this).apply { id=R.id.fsEvidenceMode;adapter=ArrayAdapter(this@FamilySettingsActivity,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.fs_evidence_modes));setSelection(listOf("NONE","AUDIO","VIDEO","PHOTO").indexOf(config.optString("evidence","NONE")).coerceAtLeast(0));root.addView(this) }
            val videoMic=FamilyUi.check(root,R.string.fs_video_mic,config.optBoolean("videoMic"))
            val autoEvidence=FamilyUi.check(root,R.string.fs_auto_evidence,config.optBoolean("autoEvidence"))
            DeliveryUi.text(root,R.string.fs_upload_unconfigured)
            val destination=DeliveryUi.section(root,R.string.fs_destination)
            val dest=config.optJSONObject("destination") ?: JSONObject()
            val destName=FamilyUi.field(destination,R.string.fs_destination_name,R.id.fsDestination,dest.optString("name"))
            val lat=FamilyUi.field(destination,R.string.fs_latitude,R.id.fsLatitude,dest.optString("latitude"),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            val lon=FamilyUi.field(destination,R.string.fs_longitude,R.id.fsLongitude,dest.optString("longitude"),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            DeliveryUi.text(destination,R.string.fs_journey_note)
            DeliveryUi.text(destination,R.string.fs_journey_guardian)
            val guardians=FamilyStore.contacts(data).let { contacts -> (0 until contacts.length()).map { contacts.getJSONObject(it) } }
            val guardian=Spinner(this).apply {
                adapter=ArrayAdapter(this@FamilySettingsActivity,android.R.layout.simple_spinner_dropdown_item,if(guardians.isEmpty()) listOf(getString(R.string.fs_none)) else guardians.map { it.optString("name") })
                setSelection(guardians.indexOfFirst { it.optString("id")==dest.optString("contactId") }.coerceAtLeast(0));destination.addView(this)
            }
            DeliveryUi.button(root,R.string.rescue_save,R.id.fsSaveSettings) {
                val latitude=DeliveryPolicy.digits(lat.text.toString()).replace('٫','.').toDoubleOrNull()
                val longitude=DeliveryPolicy.digits(lon.text.toString()).replace('٫','.').toDoubleOrNull()
                if(destName.text.isNotBlank() && (latitude==null || longitude==null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) { lat.error=getString(R.string.fs_coordinates_invalid);return@button }
                if(motion.isChecked && !location.isChecked) { Toast.makeText(this,R.string.fs_location_consent,Toast.LENGTH_LONG).show();return@button }
                val entitlements=com.nexvary.recorder.billing.EntitlementManager(this)
                if((motion.isChecked && !entitlements.canUse(com.nexvary.recorder.billing.SafetyFeature.MOTION)) || (autoEvidence.isChecked && !entitlements.canUse(com.nexvary.recorder.billing.SafetyFeature.ADVANCED_EVIDENCE))) { Toast.makeText(this,R.string.fs_billing_verifier_missing,Toast.LENGTH_LONG).show();return@button }
                val value=JSONObject().put("displayName",name.text.toString().take(120)).put("note",note.text.toString().take(2000))
                    .put("autoShare",share.isChecked).put("location",location.isChecked).put("immediate",immediate.isChecked).put("audible",audible.isChecked)
                    .put("shareDevice",device.isChecked).put("batteryAlerts",battery.isChecked).put("batterySaver",saver.isChecked)
                    .put("motion",motion.isChecked).put("sensitivity",sensitivity.selectedItemPosition).put("volumeTrigger",volume.isChecked)
                    .put("evidence",listOf("NONE","AUDIO","VIDEO","PHOTO")[evidence.selectedItemPosition]).put("autoEvidence",autoEvidence.isChecked)
                    .put("videoMic",videoMic.isChecked).put("showMedical",config.optBoolean("showMedical")).put("shareMedical",config.optBoolean("shareMedical"))
                if(destName.text.isNotBlank()) value.put("destination",JSONObject().put("name",destName.text.toString().take(120)).put("latitude",latitude).put("longitude",longitude).put("contactId",guardians.getOrNull(guardian.selectedItemPosition)?.optString("id").orEmpty()))
                MaterialAlertDialogBuilder(this).setTitle(R.string.fs_consent_review)
                    .setMessage(getString(if(share.isChecked) R.string.fs_sharing_review else R.string.fs_local_only))
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.rescue_save) { _,_ ->
                        FamilyUi.io(this,{
                            FamilyStore(this).update { state ->
                                state.put("config",value)
                                if(!value.optBoolean("autoShare")) FamilyStore.jobs(state).let { jobs -> jobs.keys().forEach { id -> jobs.getJSONObject(id).let { if(!it.optBoolean("manual") && it.optString("status") !in listOf("SENT","SENDING")) it.put("status","CANCELLED") } } }
                                FamilyStore.event(state,"CONFIGURATION_UPDATED")
                            }
                            stopService(Intent(this,SafetyLocationService::class.java)) // Resume visibly with the new consent/settings.
                        }) { Toast.makeText(this,R.string.smtp_saved,Toast.LENGTH_LONG).show() }
                    }.show()
            }
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
        val role=Spinner(this).apply { adapter=ArrayAdapter(this@TrustedContactsActivity,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.fs_roles));setSelection(listOf("PRIMARY","SECONDARY","EMERGENCY").indexOf(original?.optString("role")).coerceAtLeast(0));form.addView(this) }
        DeliveryUi.text(form,R.string.fs_channel)
        val channel=Spinner(this).apply { adapter=ArrayAdapter(this@TrustedContactsActivity,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.fs_channels));setSelection(if(original?.optString("channel")=="SMS") 1 else 0);form.addView(this) }
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
    override val title=R.string.fs_parent_mode
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
