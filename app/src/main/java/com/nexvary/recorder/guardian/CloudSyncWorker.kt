package com.nexvary.recorder.guardian

import android.content.Context
import android.os.Environment
import androidx.work.*
import com.nexvary.recorder.family.*
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Durable encrypted outbox: local SOS succeeds before any network operation. */
object CloudOutbox {
    fun events(root: JSONObject)=root.optJSONObject("cloudEvents") ?: JSONObject().also { root.put("cloudEvents",it) }
    fun enqueue(root: JSONObject,payload: JSONObject,manual: Boolean=false) {
        if(!manual && !FamilyStore.config(root).optBoolean("autoShare")) return
        if(payload.optString("eventType")=="EVIDENCE_READY") return
        val id=payload.optString("sessionId").takeIf { it.isNotBlank() && it!="null" } ?: payload.getString("eventId")
        if(events(root).has(id)) return
        events(root).put(id,JSONObject().put("eventId",id).put("status","PENDING").put("payload",JSONObject(payload.toString())).put("manual",manual).put("createdAt",System.currentTimeMillis()))
    }
    fun retry(context: Context, manual: Boolean=false) {
        FamilyStore(context).update { root -> val rows=events(root);rows.keys().forEach { key ->
            val job=rows.getJSONObject(key)
            if(job.optString("status") !in listOf("SENT","CANCELLED","CANCEL_PENDING")) {
                if(manual && job.optString("status")=="FAILED") job.put("manualRetry",true)
                job.put("status","RETRYING")
            }
        } }
        CloudSyncWorker.schedule(context)
    }
}

class CloudSyncWorker(context: Context,params: WorkerParameters): Worker(context,params) {
    private val store=FamilyStore(context)
    override fun doWork(): Result {
        if(!CloudApi.configured(applicationContext)) return Result.success()
        val expectedSession=CloudApi.config(applicationContext)
        val api=CloudApi(applicationContext,expectedSession)
        val pending=CloudOutbox.events(store.snapshot()).let { rows -> rows.keys().asSequence().map { rows.getJSONObject(it) }.toList() }
        try {
            if(CloudApi.portable(expectedSession)) {
                QuotaManager(applicationContext).refresh()
                val description=CloudApi.config(applicationContext).optJSONObject("service")
                if(description?.optBoolean("remoteSos")!=true) return Result.retry()
            }
            for(job in pending) {
                if(isStopped) return Result.retry()
                if(!CloudSessionState.sameSession(CloudApi.config(applicationContext),expectedSession)) return Result.success()
                val id=job.getString("eventId")
                val fresh=CloudOutbox.events(store.snapshot()).optJSONObject(id) ?: continue
                if(fresh.optString("status")=="CANCEL_PENDING") {
                    val serverId=fresh.optString("serverEventId")
                    if(serverId.isNotBlank()) {
                        val response=api.request("POST","/events/$serverId/cancel",JSONObject())
                        status(id,response.optString("state","CANCELLED"),cancelResponse=true)
                    } else status(id,"CANCELLED",cancelResponse=true)
                    continue
                }
                if(fresh.optString("status") in listOf("SENT","CANCELLED","LIMIT","LOGIN_REQUIRED")) continue
                if(!allowed(id,expectedSession)) continue
                val snapshot=store.snapshot();val current=CloudOutbox.events(snapshot).optJSONObject(id) ?: continue
                if(!current.optBoolean("manual") && !FamilyStore.config(snapshot).optBoolean("autoShare")) { status(id,"CANCELLED");continue }
                val payload=AlertPayload.redactConsent(JSONObject(current.getJSONObject("payload").toString()),FamilyStore.config(snapshot))
                status(id,"PENDING")
                if(!allowed(id,expectedSession)) continue
                var serverId=current.optString("serverEventId")
                if(serverId.isBlank()) {
                    val reservation=api.request("POST","/events",JSONObject().put("eventId",id).put("payload",payload).put("test",payload.optString("eventType")=="TEST_ALERT"))
                    serverId=reservation.getString("eventId")
                    store.update { root ->
                        val latest=CloudOutbox.events(root).getJSONObject(id)
                        latest.put("serverEventId",serverId).put("merged",reservation.optBoolean("merged")).put("uploadEvidence",reservation.optBoolean("uploadEvidence",true))
                        if(latest.optString("status")=="CANCELLED" && CloudSessionState.sameSession(CloudApi.config(root),expectedSession)) latest.put("status","CANCEL_PENDING")
                        if(CloudSessionState.sameSession(CloudApi.config(root),expectedSession)) reservation.optJSONObject("usage")?.let { root.put("cloudUsage",it) }
                    }
                }
                if(!allowed(id,expectedSession)) continue
                val merged=CloudOutbox.events(store.snapshot()).getJSONObject(id).optBoolean("merged")
                val files=if(merged || !CloudOutbox.events(store.snapshot()).getJSONObject(id).optBoolean("uploadEvidence",true)) JSONObject() else snapshot.optJSONObject("evidence") ?: JSONObject()
                files.keys().asSequence().map { files.getJSONObject(it) }.filter { it.optString("eventId")==id }.sortedBy { it.optInt("segmentIndex") }.forEach { manifest ->
                    if(!allowed(id,expectedSession)) return@forEach
                    val index=manifest.optInt("segmentIndex",0)
                    val latest=store.snapshot();val latestJob=CloudOutbox.events(latest).getJSONObject(id)
                    if(!latestJob.optBoolean("manual") && !FamilyStore.config(latest).optBoolean("autoShare")) { status(id,"CANCELLED");return@forEach }
                    if(latestJob.optJSONObject("uploaded")?.has(index.toString())==true) return@forEach
                    val file=File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),manifest.getString("fileName"))
                    if(!file.isFile || !EvidenceManager.verify(applicationContext,manifest)) throw CloudFailure("LOCAL_EVIDENCE_CHANGED")
                    status(id,"UPLOADING")
                    val signed=api.request("POST","/uploads/sign",JSONObject().put("eventId",serverId).put("index",index).put("size",file.length()).put("sha256",manifest.getString("sha256"))
                        .put("durationMs",manifest.optLong("durationMs")).put("contentType",when(file.extension.lowercase()) { "jpg","jpeg" -> "image/jpeg";"m4a" -> "audio/mp4";else -> "video/mp4" }))
                    if(!signed.optBoolean("complete")) {
                        if(!allowed(id,expectedSession)) return@forEach
                        upload(file,signed)
                        if(!allowed(id,expectedSession)) return@forEach
                        api.request("POST","/uploads/complete",JSONObject().put("eventId",serverId).put("index",index))
                    }
                    store.update { root -> val row=CloudOutbox.events(root).getJSONObject(id);val uploaded=row.optJSONObject("uploaded") ?: JSONObject().also { row.put("uploaded",it) };uploaded.put(index.toString(),true) }
                }
                // If evidence is still recording, keep reconciling the same server event, never reserve again.
                if(!allowed(id,expectedSession)) continue
                val retry=CloudOutbox.events(store.snapshot()).optJSONObject(id)?.optBoolean("manualRetry")==true
                val result=api.request("POST","/events/$serverId/dispatch",JSONObject().put("retry",retry))
                store.update { root -> CloudOutbox.events(root).optJSONObject(id)?.remove("manualRetry") }
                val recording=store.snapshot().optJSONObject("evidenceActions")?.optString(id) in listOf("RECORDING","STARTING")
                status(id,if(recording) "RECORDING" else result.optString("state","QUEUED"))
            }
            QuotaManager(applicationContext).refresh()
            val remains=CloudOutbox.events(store.snapshot()).let { rows -> rows.keys().asSequence().any { rows.getJSONObject(it).optString("status") in listOf("QUEUED","RETRYING","UPLOADING","PENDING","CANCEL_PENDING") } }
            return if(remains) Result.retry() else Result.success()
        } catch(error: CloudFailure) {
            if(!CloudSessionState.sameSession(CloudApi.config(applicationContext),expectedSession)) return Result.success()
            val state=when { error.status==401 -> "LOGIN_REQUIRED";error.code=="MONTHLY_QUOTA_EXHAUSTED" -> "LIMIT";error.code=="UPLOAD_URL_EXPIRED_OR_REJECTED" -> "RETRYING";error.status in listOf(400,403,404,410,413) -> "FAILED";else -> "RETRYING" }
            store.update { root -> val rows=CloudOutbox.events(root);rows.keys().forEach { key -> val row=rows.getJSONObject(key);if(row.optString("status") in listOf("PENDING","UPLOADING","QUEUED","RETRYING")) row.put("status",state).put("error",error.code) } }
            return if(state=="RETRYING") Result.retry() else Result.success()
        } catch(_: Exception) { return Result.retry() }
    }
    private fun allowed(id: String, expected: JSONObject): Boolean {
        val root=store.snapshot()
        val row=CloudOutbox.events(root).optJSONObject(id) ?: return false
        return row.optString("status") !in listOf("CANCELLED","CANCEL_PENDING") && CloudSessionState.sameSession(CloudApi.config(root),expected)
    }
    private fun status(id: String,state: String,cancelResponse: Boolean=false)=store.update { root ->
        CloudOutbox.events(root).optJSONObject(id)?.let { row ->
            if(cancelResponse || row.optString("status") !in listOf("CANCELLED","CANCEL_PENDING")) row.put("status",state)
        }
    }
    private fun upload(file: File,signed: JSONObject) {
        val url=URL(signed.getString("url"));require(url.protocol=="https" && url.userInfo==null)
        val connection=url.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects=false;connection.requestMethod="PUT";connection.connectTimeout=15_000;connection.readTimeout=60_000;connection.doOutput=true;connection.setFixedLengthStreamingMode(file.length())
        signed.getJSONObject("headers").let { headers -> headers.keys().forEach { connection.setRequestProperty(it,headers.getString(it)) } }
        try {
            file.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output,64*1024) } }
            if(connection.responseCode !in 200..299) throw CloudFailure("UPLOAD_URL_EXPIRED_OR_REJECTED",connection.responseCode)
        } finally { connection.disconnect() }
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("guardian-cloud-reconcile",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CloudSyncWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
            val request=OneTimeWorkRequestBuilder<CloudSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("guardian-cloud",ExistingWorkPolicy.KEEP,request)
        }
    }
}
