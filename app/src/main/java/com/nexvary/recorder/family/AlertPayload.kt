package com.nexvary.recorder.family

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

object AlertPayload {
    fun network(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "OFFLINE"
        if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return "UNVALIDATED"
        return when { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"; caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"; else -> "ONLINE" }
    }
    fun create(context: Context, root: JSONObject, type: String, id: String, test: Boolean = false): JSONObject {
        val config = FamilyStore.config(root)
        val now = System.currentTimeMillis()
        val last = root.optJSONObject("lastLocation")
        val boot=android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
        val age = last?.let {
            if(it.optInt("boot",-2)==boot && boot>=0) (android.os.SystemClock.elapsedRealtime()-it.optLong("elapsed",-1)).takeIf { value -> value>=0 }
            else (now-it.optLong("timestamp",0)).takeIf { value -> value>=0 }
        }
        return JSONObject().put("schemaVersion",1).put("eventId",id).put("eventType",if(test) "TEST_ALERT" else type)
            .put("timestamp",Instant.ofEpochMilli(now).toString()).put("timezone",ZoneId.systemDefault().id)
            .put("displayName",config.optString("displayName"))
            .put("location",if(age != null && age <= 120_000) last else JSONObject.NULL)
            .put("lastKnownLocation",last ?: JSONObject.NULL).put("locationAgeMs",age ?: JSONObject.NULL)
            .put("battery",context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: JSONObject.NULL)
            .put("network",network(context)).put("message",config.optString("note"))
            .put("evidence",JSONArray())
            .apply {
                if(config.optBoolean("shareDevice")) put("device",JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL))
                if(config.optBoolean("shareMedical")) root.optJSONObject("medical")?.let { put("emergencyProfile",it) }
            }
    }
}
