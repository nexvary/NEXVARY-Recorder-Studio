package com.nexvary.recorder.delivery

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmtpSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = StudioPage.install(this, R.string.smtp_title)
        val store = SmtpSettings(this)
        var existing = store.load()
        DeliveryUi.text(root, R.string.smtp_note)
        val form = DeliveryUi.section(root, R.string.smtp_connection)
        val host = DeliveryUi.field(form, R.string.smtp_host, R.id.smtpHost, existing?.host.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val port = DeliveryUi.field(form, R.string.smtp_port, R.id.smtpPort, (existing?.port ?: 587).toString(), InputType.TYPE_CLASS_NUMBER)
        val tls = SwitchCompat(this).apply {
            id = R.id.smtpTls; setText(R.string.smtp_direct_tls); isChecked = existing?.directTls ?: false
            setOnCheckedChangeListener { _, checked ->
                if (port.text.toString() in listOf("465", "587")) port.setText(if (checked) "465" else "587")
            }
            form.addView(this)
        }
        DeliveryUi.text(form, R.string.smtp_tls_note)
        val user = DeliveryUi.field(form, R.string.smtp_user, R.id.smtpUser, existing?.username.orEmpty())
        val password = DeliveryUi.field(form, R.string.smtp_password, R.id.smtpPassword, type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            if (existing != null) setHint(R.string.smtp_password_kept)
        }
        val from = DeliveryUi.field(form, R.string.smtp_from, R.id.smtpFrom, existing?.from.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val limit = DeliveryUi.field(form, R.string.smtp_limit, R.id.smtpLimit, (existing?.limitMiB ?: 25).toString(), InputType.TYPE_CLASS_NUMBER)
        val status = DeliveryUi.text(root, R.string.smtp_not_tested).apply { id = R.id.smtpStatus; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        fun profile(): SmtpProfile? {
            val sameServer = existing?.let { it.host == host.text.toString().trim() && it.port == DeliveryPolicy.digits(port.text.toString()).toIntOrNull() &&
                it.username == user.text.toString().trim() && it.directTls == tls.isChecked } ?: false
            val secret = password.text.toString().ifEmpty { if (sameServer) existing?.password.orEmpty() else "" }
            val value = SmtpProfile(host.text.toString().trim(), DeliveryPolicy.digits(port.text.toString()).toIntOrNull() ?: 0,
                tls.isChecked, user.text.toString().trim(), secret, from.text.toString().trim(), DeliveryPolicy.digits(limit.text.toString()).toIntOrNull() ?: 0)
            if (!value.valid()) { status.setText(R.string.smtp_invalid); return null }
            return value
        }
        var busy = false
        DeliveryUi.button(root, R.string.smtp_save, R.id.smtpSave) {
            val value = profile()
            if (value != null && !busy) {
                busy = true
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { store.save(value) }.isSuccess }
                    busy = false
                    if (ok) { existing = value; password.text.clear(); password.setHint(R.string.smtp_password_kept) }
                    status.setText(if (ok) R.string.smtp_saved else R.string.smtp_store_failed)
                }
            }
        }
        DeliveryUi.button(root, R.string.smtp_test, R.id.smtpTest) {
            val value = profile()
            if (value != null && !busy) {
                busy = true; status.setText(R.string.smtp_testing)
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { SmtpSender("", "").test(value) }.isSuccess
                    }
                    busy = false; status.setText(if (ok) R.string.smtp_test_ok else R.string.smtp_test_failed)
                }
            }
        }
        DeliveryUi.button(root, R.string.mail_outbox, R.id.mailOutbox) { startActivity(Intent(this, MailOutboxActivity::class.java)) }
        DeliveryUi.button(root, R.string.smtp_forget, R.id.smtpForget) {
            if (!busy) MaterialAlertDialogBuilder(this).setMessage(R.string.smtp_forget_note)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.smtp_forget) { _, _ ->
                    store.clear(); existing = null; password.text.clear(); password.setHint(R.string.smtp_password); status.setText(R.string.smtp_forgotten)
                }.show()
        }
    }
}
