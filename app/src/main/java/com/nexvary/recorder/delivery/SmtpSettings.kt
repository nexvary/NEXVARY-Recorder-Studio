package com.nexvary.recorder.delivery

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All SMTP fields, including the password, are encrypted with this installation's Keystore. */
class SmtpSettings(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "smtp-profile.aes"))
    private fun key(): SecretKey = synchronized(LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): SmtpProfile? = synchronized(LOCK) {
        if (!file.baseFile.exists()) return@synchronized null
        runCatching {
            val bytes = file.readFully()
            require(bytes.size in 29..32_768)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
            try {
                val json = JSONObject(plain.toString(Charsets.UTF_8))
                SmtpProfile(json.getString("host"), json.getInt("port"), json.getBoolean("tls"),
                    json.getString("user"), json.getString("password"), json.getString("from"), json.getInt("limit"), json.optString("cameraRecipients")).also { require(it.valid()) }
            } finally { plain.fill(0) }
        }.getOrNull()
    }
    fun save(profile: SmtpProfile) = synchronized(LOCK) {
        require(profile.valid())
        val plain = JSONObject().put("host", profile.host).put("port", profile.port).put("tls", profile.directTls)
            .put("user", profile.username).put("password", profile.password).put("from", profile.from).put("limit", profile.limitMiB).put("cameraRecipients", profile.cameraRecipients)
            .toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.iv + cipher.doFinal(plain)
            val out = file.startWrite()
            try { out.write(encrypted); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        } finally { plain.fill(0) }
    }
    fun clear() = synchronized(LOCK) { file.delete() }
    companion object { private const val ALIAS = "nexvary.smtp.v1"; private val LOCK = Any() }
}
