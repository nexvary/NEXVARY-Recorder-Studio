package com.nexvary.recorder.delivery

import android.content.Context
import androidx.work.*
import com.nexvary.recorder.R
import java.util.concurrent.TimeUnit

class MailWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val sender = SmtpSender(context.getString(R.string.mail_subject), context.getString(R.string.mail_body))
    override fun doWork(): Result {
        val id = inputData.getString("job") ?: return Result.failure()
        val queue = MailQueue(applicationContext)
        return try {
            if (queue.get(id)?.state == MailQueue.State.SENDING) { queue.interrupted(id); return Result.success() }
            val state = MailDispatcher(queue, sender).deliver(id, SmtpSettings(applicationContext).load())
            if (state == MailQueue.State.RETRY) Result.retry() else Result.success()
        } catch (_: Exception) {
            // If the process/worker dies mid-send, never silently resubmit an uncertain message.
            runCatching { queue.interrupted(id) }
            Result.failure()
        }
    }
    override fun onStopped() { sender.close() }
    companion object {
        fun schedule(context: Context, id: String) {
            val work = OneTimeWorkRequestBuilder<MailWorker>()
                .setInputData(workDataOf("job" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(context).enqueueUniqueWork("recording-mail-$id", ExistingWorkPolicy.KEEP, work).result.get()
        }
        /** Call off the UI thread. Handles a crash between queue persistence and WorkManager enqueue. */
        fun reconcile(context: Context) {
            val manager = WorkManager.getInstance(context); val queue = MailQueue(context)
            queue.jobs().forEach { job ->
                if (job.state in listOf(MailQueue.State.QUEUED, MailQueue.State.RETRY, MailQueue.State.SENDING)) {
                    val work = manager.getWorkInfosForUniqueWork("recording-mail-${job.id}").get()
                    val running = work.any { it.state == WorkInfo.State.RUNNING }
                    if (job.state == MailQueue.State.SENDING && !running) queue.interrupted(job.id)
                    else if (job.state != MailQueue.State.SENDING && work.none { !it.state.isFinished }) schedule(context, job.id)
                }
            }
        }
    }
}
