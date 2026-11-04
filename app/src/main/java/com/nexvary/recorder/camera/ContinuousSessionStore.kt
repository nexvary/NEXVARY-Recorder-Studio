package com.nexvary.recorder.camera

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.AtomicFile
import com.nexvary.recorder.delivery.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

/** App-private, backup-excluded recovery journal. Never resumes camera capture after process death.
 * Each completed file is journaled before enqueue; deterministic job IDs close the crash window.
 */
class ContinuousSessionStore(context: Context) {
    private val context = context.applicationContext
    private val dir = File(context.noBackupFilesDir, "continuous_sessions").apply { check(isDirectory || mkdirs()) }
    private val movies = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
    fun create(seconds: Int, recipient: String, retry: Boolean): String {
        require(seconds in SegmentTimeline.DURATIONS)
        require(recipient.isBlank() || MailRecipients.parse(recipient) != null)
        val id = UUID.randomUUID().toString()
        openSessionId = id
        write(File(dir, "$id.json"), JSONObject().put("id", id).put("created", System.currentTimeMillis())
            .put("seconds", seconds).put("recipient", recipient).put("retry", retry).put("state", "RECORDING"))
        return id
    }
    fun open(id: String, index: Int, pts: Long): File {
        UUID.fromString(id); require(index > 0)
        val name = "NEXVARY_${id}_${index.toString().padStart(6, '0')}"
        val temp = File(movies, "$name.pending")
        write(File(dir, "$id-$index.segment"), JSONObject().put("session", id).put("index", index)
            .put("name", name).put("created", System.currentTimeMillis()).put("firstVideoUs", pts).put("state", "RECORDING"))
        return temp
    }
    fun finalize(id: String, temp: File, index: Int, first: Long, last: Long) {
        // Atomic rename follows muxer stop, never exposes an actively written MP4 in the library.
        RandomAccessFile(temp, "rw").use { it.fd.sync() }
        val saved = File(movies, temp.nameWithoutExtension + ".mp4")
        check(temp.renameTo(saved))
        val entry = File(dir, "$id-$index.segment")
        val json = read(entry).put("firstVideoUs", first).put("lastVideoUs", last).put("state", "SAVED")
        write(entry, json)
        executor.execute { runCatching { validateAndQueue(entry) } }
    }
    fun stop(id: String, reason: String) {
        val file = File(dir, "$id.json")
        write(file, read(file).put("state", reason).put("stopped", System.currentTimeMillis()))
        if (openSessionId == id) openSessionId = null
    }
    private fun validateAndQueue(entry: File) {
        val json = read(entry)
        val session = read(File(dir, json.getString("session") + ".json"))
        val name = json.getString("name")
        require(Regex("NEXVARY_[a-f0-9-]+_[0-9]{6,}").matches(name))
        val saved = File(movies, "$name.mp4")
        val temp = File(movies, "$name.pending")
        val candidate = if (saved.exists()) saved else temp
        if (!candidate.isFile) { write(entry, json.put("state", "MISSING")); return }
        val metadata = MediaMetadataRetriever()
        val valid = try {
            metadata.setDataSource(candidate.absolutePath)
            (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0 &&
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (_: Exception) { false } finally { metadata.release() }
        if (!valid) {
            // Quarantine incomplete tails for inspection; never delete a failed/unsent recording.
            write(entry, json.put("state", "INCOMPLETE")); return
        }
        if (candidate == temp) check(temp.renameTo(saved))
        val digest = MessageDigest.getInstance("SHA-256")
        saved.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        json.put("sha256", digest.digest().joinToString("") { "%02x".format(it) }).put("bytes", saved.length()).put("state", "SAVED")
        write(entry, json)
        val recipient = session.getString("recipient")
        if (recipient.isBlank()) return
        val job = MailQueue(context).enqueue(saved, recipient, SmtpSettings(context).load()?.limitMiB ?: 25,
            SegmentTimeline.jobId(session.getString("id"), json.getInt("index")), session.optBoolean("retry", true))
        write(entry, json.put("job", job.id).put("state", "QUEUED"))
        if (job.state in listOf(MailQueue.State.QUEUED, MailQueue.State.RETRY)) MailWorker.schedule(context, job.id)
    }
    /** Run off UI thread before starting a new session. Active capture must never be recovered. */
    fun recover(activeSession: String? = null) {
        dir.listFiles().orEmpty().filter { it.extension == "json" && it.nameWithoutExtension != activeSession && it.nameWithoutExtension != openSessionId }.forEach {
            runCatching { val j = read(it); if (j.optString("state") == "RECORDING") write(it, j.put("state", "INTERRUPTED")) }
        }
        dir.listFiles().orEmpty().filter { it.extension == "segment" && !it.name.startsWith("$activeSession-") && !it.name.startsWith("$openSessionId-") }.forEach {
            runCatching {
                val json = read(it)
                val jobId = json.optString("job")
                if (jobId.isBlank() || MailQueue(context).get(jobId) == null) validateAndQueue(it)
            }
        }
        MailWorker.reconcile(context)
    }
    fun summary(): IntArray {
        val entries = dir.listFiles().orEmpty().filter { it.extension == "segment" }.mapNotNull { runCatching { read(it) }.getOrNull() }
        val queue = MailQueue(context)
        var accepted = 0; var pending = 0; var problems = 0
        entries.forEach {
            val state = it.optString("state")
            val job = it.optString("job").takeIf(String::isNotBlank)?.let(queue::get)
            when {
                job?.state == MailQueue.State.SENT -> accepted++
                job?.state in listOf(MailQueue.State.QUEUED, MailQueue.State.SENDING, MailQueue.State.RETRY) -> pending++
                job != null || state in listOf("INCOMPLETE", "MISSING") -> problems++
                state == "SAVED" && read(File(dir, it.getString("session") + ".json")).optString("recipient").isNotBlank() -> problems++
            }
        }
        return intArrayOf(entries.count { it.optString("state") != "RECORDING" }, accepted, pending, problems)
    }
    fun hasInterruptedSession() = dir.listFiles().orEmpty().filter { it.extension == "json" }.any {
        runCatching { read(it).optString("state") !in listOf("RECORDING", "STOPPED") }.getOrDefault(false)
    }
    private fun read(file: File) = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
    private fun write(file: File, json: JSONObject) {
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    companion object {
        @Volatile private var openSessionId: String? = null
        val executor = Executors.newSingleThreadExecutor()
    }
}
