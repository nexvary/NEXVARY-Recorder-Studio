package com.nexvary.recorder.family

import android.content.Context
import org.json.JSONArray

/** One-way migration removes the legacy plaintext SOS cards after encrypted persistence succeeds. */
class RescueCardsStore(private val context: Context) {
    private val store=FamilyStore(context)
    fun read(): JSONArray {
        val legacy=context.getSharedPreferences("sos_cards",Context.MODE_PRIVATE)
        if(legacy.contains("cards")) {
            val cards=JSONArray(legacy.getString("cards","[]"))
            write(cards);check(legacy.edit().remove("cards").commit());return cards
        }
        return store.snapshot().optJSONArray("rescueCards") ?: JSONArray()
    }
    fun write(cards: JSONArray) { store.update { it.put("rescueCards",cards) } }
    fun clear() { store.update { it.remove("rescueCards") };context.getSharedPreferences("sos_cards",Context.MODE_PRIVATE).edit().clear().commit() }
}
