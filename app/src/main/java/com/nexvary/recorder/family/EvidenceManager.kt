package com.nexvary.recorder.family

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.os.StatFs
import com.nexvary.recorder.camera.BackgroundCameraService
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object EvidenceManager {
    fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) { val count=input.read(buffer);if(count < 0) break;digest.update(buffer,0,count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun storageBytes(context: Context) = StatFs(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!.absolutePath).availableBytes
    /** Called only after the recorder has stopped and released its output. */
    fun finalized(context: Context,file: File,eventId: String): JSONObject {
        UUID.fromString(eventId)
        require(file.canonicalFile.parentFile == context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.canonicalFile && file.isFile && file.length() > 0)
        val duration=if(file.extension.lowercase() in listOf("mp4","m4a")) {
            val media=MediaMetadataRetriever();try { media.setDataSource(file.absolutePath);media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { media.release() }
        } else 0
        val metadata=JSONObject().put("schemaVersion",1).put("integrityType","SHA-256 — not a legal chain of custody")
            .put("eventId",eventId).put("fileName",file.name).put("fileSize",file.length()).put("sha256",hash(file))
            .put("closedAt",Instant.now().toString()).put("deviceTimeMs",System.currentTimeMillis()).put("timezone",ZoneId.systemDefault().id)
            .put("durationMs",duration).put("device",JSONObject().put("manufacturer",android.os.Build.MANUFACTURER).put("model",android.os.Build.MODEL))
        val store=FamilyStore(context)
        store.update { root ->
            root.optJSONObject("lastLocation")?.let { metadata.put("locationSnapshot",it) }
            val evidence=root.optJSONObject("evidence") ?: JSONObject().also { root.put("evidence",it) }
            evidence.put(file.name,metadata);root.put("evidenceAction","SAVED")
            FamilyStore.event(root,"EVIDENCE_SAVED")
        }
        return metadata
    }
    fun verify(context: Context,manifest: JSONObject): Boolean {
        val file=File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),manifest.getString("fileName"))
        return file.canonicalFile.parentFile == context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.canonicalFile &&
            file.isFile && file.length() == manifest.getLong("fileSize") && hash(file) == manifest.getString("sha256")
    }
    fun stopOwned(context: Context,eventId: String?) {
        if(BackgroundCameraService.activeEvidenceEvent?.let { eventId == null || it == eventId } == true)
            context.startService(Intent(context,BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
        if(AudioEvidenceService.activeEvent?.let { eventId == null || it == eventId } == true)
            context.startService(Intent(context,AudioEvidenceService::class.java).setAction("STOP"))
    }
}

/** A real backend must implement this protocol. No server or success response is fabricated. */
interface ProgressiveEvidenceUpload {
    data class Session(val uploadId: String,val acceptedOffset: Long)
    data class Receipt(val acceptedOffset: Long,val sha256: String)
    fun open(eventId: String,fileName: String,totalBytes: Long,sha256: String): Session
    fun send(uploadId: String,offset: Long,bytes: ByteArray,sha256: String): Receipt
    fun complete(uploadId: String,sha256: String): String
}
