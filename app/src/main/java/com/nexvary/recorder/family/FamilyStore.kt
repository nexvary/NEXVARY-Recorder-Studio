package com.nexvary.recorder.family

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One atomic encrypted transaction covers state, consents, recipients, jobs and history. */
class FamilyStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "family-safety.aes"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("nexvary.family.v1", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nexvary.family.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject {
        if (!file.baseFile.exists()) return JSONObject()
        val bytes = file.readFully(); require(bytes.size in 29..64_000_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        val plain = cipher.doFinal(bytes,12,bytes.size-12)
        return try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }
    fun snapshot(): JSONObject = synchronized(LOCK) { read() }
    fun <T> update(action: (JSONObject) -> T): T = synchronized(LOCK) {
        val json = read(); val before = json.toString(); val result = action(json)
        if(before == json.toString()) return@synchronized result
        val plain = json.toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
            val out = file.startWrite()
            try { out.write(cipher.iv); out.write(cipher.doFinal(plain)); file.finishWrite(out) }
            catch (e: Exception) { file.failWrite(out); throw e }
        } finally { plain.fill(0) }
        result
    }
    companion object {
        private val LOCK = Any()
        fun events(root: JSONObject) = root.optJSONArray("events") ?: JSONArray().also { root.put("events",it) }
        fun event(root: JSONObject, type: String, detail: String = "") {
            val previous = events(root)
            val rows = JSONArray()
            for(i in maxOf(0,previous.length()-999) until previous.length()) rows.put(previous.get(i))
            rows.put(JSONObject().put("type",type).put("time",System.currentTimeMillis()).put("detail",detail))
            root.put("events",rows)
        }
        fun config(root: JSONObject) = root.optJSONObject("config") ?: JSONObject()
        fun contacts(root: JSONObject) = root.optJSONArray("contacts") ?: JSONArray()
        fun jobs(root: JSONObject) = root.optJSONObject("alerts") ?: JSONObject().also { root.put("alerts",it) }
        fun state(root: JSONObject): SafetyState? = root.optJSONObject("state")?.let {
            SafetyState(it.getString("id"),SafetyPhase.valueOf(it.getString("phase")),
                SafetyDeadline(it.getLong("wall"),it.getLong("elapsed"),it.getInt("boot")),it.optInt("grace",60),it.optString("reason","CHECK_IN"),it.optBoolean("test"))
        }
        fun putState(root: JSONObject, state: SafetyState) {
            root.put("state",JSONObject().put("id",state.id).put("phase",state.phase.name)
                .put("wall",state.deadline.wall).put("elapsed",state.deadline.elapsed).put("boot",state.deadline.boot)
                .put("grace",state.graceSeconds).put("reason",state.reason).put("test",state.test))
        }
    }
}
