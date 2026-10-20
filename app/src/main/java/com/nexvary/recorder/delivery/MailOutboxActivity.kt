package com.nexvary.recorder.delivery

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MailOutboxActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var queue: MailQueue
    private val handler = Handler(Looper.getMainLooper())
    private var last: List<MailQueue.Job>? = null
    private var refreshing = false
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1500) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        queue = MailQueue(this)
        val root = StudioPage.install(this, R.string.mail_outbox)
        DeliveryUi.text(root, R.string.mail_queue_note)
        DeliveryUi.button(root, R.string.smtp_title, R.id.openSmtp) { startActivity(Intent(this, SmtpSettingsActivity::class.java)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(list)
    }
    override fun onStart() {
        super.onStart(); handler.post(tick)
        lifecycleScope.launch { withContext(Dispatchers.IO) { runCatching { MailWorker.reconcile(this@MailOutboxActivity) } }; render() }
    }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }
    private fun render() {
        if (refreshing) return
        refreshing = true
        lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { runCatching { queue.jobs() }.getOrNull() }
                if (rows != null) showRows(rows)
                else Toast.makeText(this@MailOutboxActivity, R.string.mail_failed, Toast.LENGTH_LONG).show()
            } finally { refreshing = false }
        }
    }
    private fun showRows(rows: List<MailQueue.Job>) {
        if (rows == last) return
        last = rows; list.removeAllViews()
        if (rows.isEmpty()) DeliveryUi.text(list, R.string.mail_empty)
        rows.forEach { job ->
            val panel = DeliveryUi.section(list, R.string.mail_recording)
            panel.addView(TextView(this).apply { text = job.name; textSize = 16f })
            panel.addView(TextView(this).apply { text = job.recipient; textDirection = android.view.View.TEXT_DIRECTION_LTR })
            DeliveryUi.text(panel, stateLabel(job.state))
            if (job.state !in listOf(MailQueue.State.SENT, MailQueue.State.SENDING, MailQueue.State.QUEUED, MailQueue.State.RETRY, MailQueue.State.CANCELLED)) {
                DeliveryUi.button(panel, R.string.mail_retry, R.id.mailRetry) {
                    MaterialAlertDialogBuilder(this).setMessage(if (job.state == MailQueue.State.UNCERTAIN) R.string.mail_retry_uncertain else R.string.mail_retry_confirm)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.mail_retry) { _, _ -> retry(job.id) }.show()
                }
            }
            if (job.state !in listOf(MailQueue.State.SENT, MailQueue.State.SENDING, MailQueue.State.CANCELLED)) {
                DeliveryUi.button(panel, R.string.mail_cancel, red = true) {
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching {
                            if (queue.cancel(job.id)) WorkManager.getInstance(this@MailOutboxActivity).cancelUniqueWork("recording-mail-${job.id}").result.get()
                        }.isSuccess }
                        if (!ok) Toast.makeText(this@MailOutboxActivity, R.string.mail_failed, Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }
        }
    }
    private fun retry(id: String) {
        lifecycleScope.launch {
            val profile = SmtpSettings(this@MailOutboxActivity).load()
            if (profile == null) { Toast.makeText(this@MailOutboxActivity, R.string.mail_config, Toast.LENGTH_LONG).show(); return@launch }
            val ok = withContext(Dispatchers.IO) { runCatching {
                WorkManager.getInstance(this@MailOutboxActivity).cancelUniqueWork("recording-mail-$id").result.get()
                val job = queue.retry(id, profile.limitMiB)
                if (job?.state == MailQueue.State.QUEUED) MailWorker.schedule(this@MailOutboxActivity, id)
            }.isSuccess }
            if (!ok) Toast.makeText(this@MailOutboxActivity, R.string.mail_failed, Toast.LENGTH_LONG).show()
            render()
        }
    }
    companion object {
        fun stateLabel(state: MailQueue.State) = when (state) {
            MailQueue.State.QUEUED -> R.string.mail_queued
            MailQueue.State.SENDING -> R.string.mail_sending
            MailQueue.State.RETRY -> R.string.mail_wait_retry
            MailQueue.State.SENT -> R.string.mail_sent
            MailQueue.State.CONFIG -> R.string.mail_config
            MailQueue.State.AUTH -> R.string.mail_auth
            MailQueue.State.TLS -> R.string.mail_tls
            MailQueue.State.LIMIT -> R.string.mail_limit
            MailQueue.State.MISSING -> R.string.mail_missing
            MailQueue.State.COPY_FAILED -> R.string.mail_copy_failed
            MailQueue.State.FAILED -> R.string.mail_failed
            MailQueue.State.UNCERTAIN -> R.string.mail_uncertain
            MailQueue.State.CANCELLED -> R.string.mail_cancelled
        }
    }
}
