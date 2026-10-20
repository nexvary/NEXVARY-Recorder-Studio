package com.nexvary.recorder.delivery

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Private send copies survive deletion/moving of the library original. Originals are never deleted here. */
class MailQueue(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "mail_outbox").apply { check(isDirectory || mkdirs() || isDirectory) }
    enum class State { QUEUED, SENDING, RETRY, SENT, CONFIG, AUTH, TLS, LIMIT, MISSING, COPY_FAILED, FAILED, UNCERTAIN, CANCELLED }
    data class Job(val id: String, val name: String, val recipient: String, val original: String,
        val created: Long, val state: State, val attempts: Int = 0, val digest: String = "")
    private fun file(id: String, extension: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(dir, "$id.$extension")
    }
    fun attachment(id: String) = file(id, "mp4")
    fun get(id: String): Job? = synchronized(LOCK) { read(file(id, "json")) }
    fun jobs(): List<Job> = synchronized(LOCK) { dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull(::read).sortedByDescending { it.created } }
    private fun read(file: File): Job? = runCatching {
        val j = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        Job(j.getString("id"), j.getString("name"), j.getString("recipient"), j.getString("original"),
            j.getLong("created"), State.valueOf(j.getString("state")), j.optInt("attempts"), j.optString("digest"))
    }.getOrNull()
    private fun write(job: Job) {
        val json = JSONObject().put("id", job.id).put("name", job.name).put("recipient", job.recipient)
            .put("original", job.original).put("created", job.created).put("state", job.state.name)
            .put("attempts", job.attempts).put("digest", job.digest).toString()
        val atom = AtomicFile(file(job.id, "json")); val out = atom.startWrite()
        try { out.write(json.toByteArray(Charsets.UTF_8)); atom.finishWrite(out) }
        catch (e: Exception) { atom.failWrite(out); throw e }
    }
    fun enqueue(source: File, recipient: String, limitMiB: Int): Job = synchronized(LOCK) {
        require(DeliveryPolicy.email(recipient))
        val allowed = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)!!, source.name)
        require(source.canonicalFile == allowed.canonicalFile && source.isFile)
        val job = Job(UUID.randomUUID().toString(), source.name, recipient, source.canonicalPath,
            System.currentTimeMillis(), State.QUEUED)
        prepare(job, limitMiB).also(::write)
    }
    private fun prepare(job: Job, limit: Int): Job {
        val destination = attachment(job.id)
        if (destination.isFile && job.digest.isNotBlank()) {
            return job.copy(state = if (DeliveryPolicy.fits(destination.length(), limit)) State.QUEUED else State.LIMIT)
        }
        val source = File(job.original)
        val movies = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.canonicalFile
        if (source.canonicalFile.parentFile != movies || !source.isFile || source.length() == 0L) return job.copy(state = State.MISSING)
        if (!DeliveryPolicy.fits(source.length(), limit)) return job.copy(state = State.LIMIT)
        val temp = file(job.id, "tmp")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input -> FileOutputStream(temp).use { out ->
                val bytes = ByteArray(64 * 1024)
                while (true) { val count = input.read(bytes); if (count < 0) break; out.write(bytes, 0, count); digest.update(bytes, 0, count) }
                out.fd.sync()
            } }
            check(temp.length() == source.length() && temp.renameTo(destination))
            job.copy(state = State.QUEUED, digest = digest.digest().joinToString("") { "%02x".format(it) })
        } catch (_: Exception) { job.copy(state = State.COPY_FAILED) }
        finally { temp.delete() }
    }
    fun retry(id: String, limit: Int): Job? = synchronized(LOCK) {
        val job = get(id) ?: return@synchronized null
        if (job.state in listOf(State.SENT, State.SENDING, State.CANCELLED, State.QUEUED, State.RETRY)) return@synchronized null
        prepare(job.copy(attempts = 0), limit).also(::write)
    }
    fun claim(id: String): Job? = synchronized(LOCK) {
        val job = get(id) ?: return@synchronized null
        if (job.state !in listOf(State.QUEUED, State.RETRY)) return@synchronized null
        job.copy(state = State.SENDING, attempts = job.attempts + 1).also(::write)
    }
    fun finish(id: String, state: State) = synchronized(LOCK) {
        val job = get(id) ?: return@synchronized
        if (job.state != State.SENDING) return@synchronized
        write(job.copy(state = state))
        if (state == State.SENT) attachment(id).delete()
    }
    fun interrupted(id: String) = synchronized(LOCK) { if (get(id)?.state == State.SENDING) finish(id, State.UNCERTAIN) }
    fun cancel(id: String): Boolean = synchronized(LOCK) {
        val job = get(id) ?: return@synchronized false
        if (job.state in listOf(State.SENDING, State.SENT, State.CANCELLED)) return@synchronized false
        write(job.copy(state = State.CANCELLED)); attachment(id).delete(); true
    }
    companion object { private val LOCK = Any() }
}
