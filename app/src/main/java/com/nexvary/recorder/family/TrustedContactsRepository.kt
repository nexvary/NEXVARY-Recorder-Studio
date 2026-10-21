package com.nexvary.recorder.family

import android.content.Context
import com.nexvary.recorder.delivery.DeliveryPolicy
import com.nexvary.recorder.safety.RescuePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TrustedContactsRepository(private val context: Context) {
    private val store = FamilyStore(context)
    fun all(): List<JSONObject> = FamilyStore.contacts(store.snapshot()).let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }.sortedBy { listOf("PRIMARY","SECONDARY","EMERGENCY").indexOf(it.optString("role")).let { index -> if(index<0) 3 else index } }
    fun save(contact: JSONObject) {
        require(contact.optString("name").isNotBlank() && contact.optString("name").length <= 120)
        val channel = contact.optString("channel")
        require(channel in listOf("EMAIL","SMS"))
        require(if(channel == "EMAIL") DeliveryPolicy.email(contact.optString("email")) else RescuePolicy.validPhone(RescuePolicy.normalizePhone(contact.optString("phone"))))
        val id = contact.optString("id").ifEmpty { UUID.randomUUID().toString() }
        UUID.fromString(id);contact.put("id",id)
        store.update { root ->
            val rows = FamilyStore.contacts(root); val next = JSONArray()
            for(i in 0 until rows.length()) if(rows.getJSONObject(i).optString("id") != id) next.put(rows.get(i))
            check(next.length()==0 || com.nexvary.recorder.billing.EntitlementManager(context).canUse(com.nexvary.recorder.billing.SafetyFeature.MULTIPLE_GUARDIANS))
            next.put(contact); root.put("contacts",next)
            FamilyStore.event(root,"CONTACT_SAVED")
        }
    }
    fun delete(id: String) = store.update { root ->
        val rows = FamilyStore.contacts(root);val next = JSONArray()
        for(i in 0 until rows.length()) if(rows.getJSONObject(i).optString("id") != id) next.put(rows.get(i))
        root.put("contacts",next)
        val jobs = FamilyStore.jobs(root)
        jobs.keys().forEach { key -> jobs.getJSONObject(key).let { if(it.optString("contactId") == id && it.optString("status") !in listOf("SENT","SENDING")) it.put("status","CANCELLED") } }
        FamilyStore.event(root,"CONTACT_REMOVED")
    }
}
