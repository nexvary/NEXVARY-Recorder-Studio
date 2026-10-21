package com.nexvary.recorder.family

import android.content.Context
import androidx.work.*
import com.nexvary.recorder.delivery.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

interface AlertTransport {
    fun send(profile: SmtpProfile, id: String, recipient: String, payload: String, beforeData: () -> Unit)
    fun close()
}

class EmergencyQueue(private val context: Context) {
    private val store = FamilyStore(context)
    fun all(): List<JSONObject> = FamilyStore.jobs(store.snapshot()).let { jobs -> jobs.keys().asSequence().map { jobs.getJSONObject(it) }.toList().sortedByDescending { it.optLong("created") } }
    companion object {
        /** Called inside the same transaction that records the event: no state/queue gap. */
        fun enqueue(root: JSONObject, payload: JSONObject, manual: Boolean = false) {
            val config = FamilyStore.config(root)
            if(!manual && !config.optBoolean("autoShare")) return
            val jobs = FamilyStore.jobs(root)
            val contacts = FamilyStore.contacts(root)
            for(i in 0 until contacts.length()) {
                val contact = contacts.getJSONObject(i)
                if(jobs.keys().asSequence().any { jobs.getJSONObject(it).let { it.optString("eventId") == payload.optString("eventId") && it.optString("contactId") == contact.optString("id") } }) continue
                val id = UUID.randomUUID().toString()
                jobs.put(id,JSONObject().put("id",id).put("eventId",payload.getString("eventId")).put("created",System.currentTimeMillis())
                    .put("contactId",contact.getString("id")).put("channel",contact.optString("channel"))
                    .put("recipient",contact.optString(if(contact.optString("channel") == "EMAIL") "email" else "phone"))
                    .put("payload",payload).put("manual",manual).put("attempts",0)
                    .put("status",if(contact.optString("channel") == "EMAIL") "QUEUED" else "WAITING_USER"))
            }
        }
    }
    fun retry(id: String): Boolean = store.update { root ->
        val job = FamilyStore.jobs(root).optJSONObject(id) ?: return@update false
        if(job.optString("status") in listOf("SENT","SENDING","CANCELLED","WAITING_USER")) return@update false
        job.put("status","QUEUED").put("attempts",0); true
    }
    fun cancel(id: String) = store.update { root ->
        FamilyStore.jobs(root).optJSONObject(id)?.let { if(it.optString("status") !in listOf("SENDING","SENT")) it.put("status","CANCELLED") }
    }
    fun claim(id: String): JSONObject? = store.update { root ->
        val job = FamilyStore.jobs(root).optJSONObject(id) ?: return@update null
        if(job.optString("status") !in listOf("QUEUED","RETRY")) return@update null
        val contact = FamilyStore.contacts(root).let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optString("id") == job.optString("contactId") } }
        if(contact == null || (!job.optBoolean("manual") && !FamilyStore.config(root).optBoolean("autoShare"))) {
            job.put("status","CANCELLED");return@update null
        }
        job.put("status","SENDING").put("attempts",job.optInt("attempts")+1)
        JSONObject(job.toString())
    }
    fun finish(id: String, status: String) = store.update { root ->
        FamilyStore.jobs(root).optJSONObject(id)?.let { job ->
            if(job.optString("status") == "SENDING") {
                job.put("status",status); FamilyStore.event(root,"ALERT_$status")
            }
        }
    }
    fun deliver(id: String, profile: SmtpProfile?, transport: AlertTransport): String? {
        val job = claim(id) ?: return null
        var data = false
        val status = try {
            if(profile == null) "CONFIG" else {
                transport.send(profile,id,job.getString("recipient"),job.getJSONObject("payload").toString()) { data = true }
                "SENT"
            }
        } catch(e: Exception) {
            when {
                e is org.eclipse.angus.mail.smtp.SMTPSendFailedException && e.returnCode >= 400 -> "FAILED"
                data -> "UNCERTAIN"
                e is MessageTooLarge -> "LIMIT"
                SmtpFailure.classify(e) == SmtpFailure.AUTH -> "AUTH"
                SmtpFailure.classify(e) in listOf(SmtpFailure.TLS,SmtpFailure.STARTTLS) -> "TLS"
                job.optInt("attempts") < 5 -> "RETRY"
                else -> "FAILED"
            }
        } finally { transport.close() }
        finish(id,status);return status
    }
}

class SafetyAlertWorker(context: Context, params: WorkerParameters): Worker(context,params) {
    private val smtp = SmtpSender("NEXVARY Safety Alert", "Emergency assistance tools — delivery to the inbox is not guaranteed.")
    override fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val queue = EmergencyQueue(applicationContext)
        val transport = object : AlertTransport {
            override fun send(profile: SmtpProfile,id: String,recipient: String,payload: String,beforeData: () -> Unit) = smtp.sendAlert(profile,id,recipient,payload,beforeData)
            override fun close() = smtp.close()
        }
        return try {
            if(queue.all().any { it.optString("id") == id && it.optString("status") == "SENDING" }) { queue.finish(id,"UNCERTAIN");return Result.success() }
            if(queue.deliver(id,SmtpSettings(applicationContext).load(),transport) == "RETRY") Result.retry() else Result.success()
        } catch(_: Exception) { runCatching { queue.finish(id,"UNCERTAIN") }; Result.failure() }
    }
    override fun onStopped() = smtp.close()
    companion object {
        fun reconcile(context: Context) {
            val manager = WorkManager.getInstance(context);val queue = EmergencyQueue(context)
            queue.all().forEach { job ->
                val id = job.getString("id");val status = job.optString("status")
                if(status in listOf("QUEUED","RETRY","SENDING")) {
                    val works = manager.getWorkInfosForUniqueWork("safety-alert-$id").get()
                    if(status == "SENDING" && works.none { it.state == WorkInfo.State.RUNNING }) queue.finish(id,"UNCERTAIN")
                    else if(status != "SENDING" && works.none { !it.state.isFinished }) {
                        val builder = OneTimeWorkRequestBuilder<SafetyAlertWorker>().setInputData(workDataOf("id" to id))
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
                        if(android.os.Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        manager.enqueueUniqueWork("safety-alert-$id",ExistingWorkPolicy.KEEP,builder.build()).result.get()
                    }
                }
            }
        }
    }
}
