package com.nexvary.recorder.media

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Private, non-backed-up vault. PIN-derived keys are bound to this install's Keystore.
 * Independently authenticated bounded chunks keep long recordings off the Java heap.
 * The authenticated terminal record detects truncation; sequence AAD detects reordering.
 */
class VaultStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "recording_vault").apply { check(isDirectory || mkdirs()) }
    private val config = File(dir, "access.properties")
    class Session internal constructor(internal val key: SecretKey)
    data class Entry(val id: String, val name: String, val mime: String)
    val configured: Boolean get() = config.exists()
    private fun properties() = Properties().apply { if (config.exists()) config.inputStream().use(::load) }
    private fun save(p: Properties) {
        val temp = File(dir, "access.tmp")
        FileOutputStream(temp).use { p.store(it, null); it.fd.sync() }
        check(temp.renameTo(config))
    }
    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)
    private fun key(pin: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        val derived = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "nexvary.vault.pin.v1"
        val pepper = (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKey()
        return try { SecretKeySpec(Mac.getInstance("HmacSHA256").apply { init(pepper) }.doFinal(derived), "AES") }
        finally { derived.fill(0) }
    }
    @Synchronized fun create(pin: CharArray): Session {
        require(!configured && pin.size in 6..12 && pin.all { it in '0'..'9' })
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        val key = key(pin, salt)
        save(Properties().apply {
            setProperty("salt", encode(salt)); setProperty("verifier", encode(MessageDigest.getInstance("SHA-256").digest(key.encoded)))
        })
        return Session(key)
    }
    @Synchronized fun unlock(pin: CharArray): Session? {
        val p = properties()
        if (System.currentTimeMillis() < p.getProperty("blocked", "0").toLong()) return null
        val key = key(pin, decode(p.getProperty("salt")))
        val ok = MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(key.encoded), decode(p.getProperty("verifier")))
        val failures = if (ok) 0 else p.getProperty("failures", "0").toInt() + 1
        p.setProperty("failures", failures.toString())
        p.setProperty("blocked", if (failures >= 5) (System.currentTimeMillis() + 30_000).toString() else "0")
        save(p)
        return if (ok) Session(key) else null
    }
    private fun file(id: String): File { require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)); return File(dir, "$id.nvx") }
    private fun aad(id: String, index: Int) = "$id:$index".toByteArray(Charsets.UTF_8)
    private fun writeBlock(out: DataOutputStream, session: Session, id: String, index: Int, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, session.key); updateAAD(aad(id, index)) }
        val encrypted = cipher.doFinal(plain)
        out.writeInt(encrypted.size); out.write(cipher.iv); out.write(encrypted)
    }
    private fun readBlock(input: DataInputStream, session: Session, id: String, index: Int): ByteArray {
        val size = input.readInt(); require(size in 16..(CHUNK + 16))
        val iv = ByteArray(12).also(input::readFully)
        val encrypted = ByteArray(size).also(input::readFully)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, session.key, GCMParameterSpec(128, iv)); updateAAD(aad(id, index))
        }.doFinal(encrypted)
    }
    @Synchronized fun import(session: Session, name: String, mime: String, input: InputStream): Entry {
        require(mime == "video/mp4" || mime == "audio/mp4" || mime == "audio/aac" || mime == "audio/mpeg")
        val id = UUID.randomUUID().toString()
        val target = file(id); val temp = File(dir, "$id.tmp")
        try {
            FileOutputStream(temp).use { raw ->
                val out = DataOutputStream(raw)
                out.writeInt(0x4e565831)
                val metadata = ByteArrayOutputStream().apply { DataOutputStream(this).use { it.writeUTF(name.take(240)); it.writeUTF(mime) } }.toByteArray()
                writeBlock(out, session, id, 0, metadata)
                val buffer = ByteArray(CHUNK); var index = 1; var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break; if (count == 0) continue
                    writeBlock(out, session, id, index++, buffer.copyOf(count)); total += count
                }
                require(total > 0)
                writeBlock(out, session, id, index, byteArrayOf()); out.flush(); raw.fd.sync()
            }
            check(temp.renameTo(target))
            // Read back every authentication tag before allowing deletion of the source.
            export(session, Entry(id, name, mime), object : OutputStream() { override fun write(b: Int) {} ; override fun write(b: ByteArray, off: Int, len: Int) {} })
            return Entry(id, name, mime)
        } catch (e: Exception) { temp.delete(); target.delete(); throw e }
    }
    private fun open(entryId: String) = DataInputStream(file(entryId).inputStream().buffered()).apply { check(readInt() == 0x4e565831) }
    @Synchronized fun entries(session: Session): List<Entry> = dir.listFiles().orEmpty().filter { it.extension == "nvx" }.sortedByDescending { it.lastModified() }.map { file ->
        val id = file.nameWithoutExtension
        open(id).use { input -> DataInputStream(ByteArrayInputStream(readBlock(input, session, id, 0))).use { Entry(id, it.readUTF(), it.readUTF()) } }
    }
    @Synchronized fun export(session: Session, entry: Entry, out: OutputStream) {
        open(entry.id).use { input ->
            readBlock(input, session, entry.id, 0)
            var index = 1
            while (true) {
                val block = readBlock(input, session, entry.id, index++)
                if (block.isEmpty()) { check(input.read() == -1); break }
                out.write(block)
            }
        }
    }
    @Synchronized fun delete(entry: Entry) { check(file(entry.id).delete()) }
    companion object { private const val CHUNK = 256 * 1024 }
}
