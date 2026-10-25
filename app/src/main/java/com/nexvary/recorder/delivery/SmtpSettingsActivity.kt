package com.nexvary.recorder.delivery

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.core.widget.doAfterTextChanged
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

class SmtpSettingsActivity : com.nexvary.recorder.family.GuardianProtectedActivity() {
    override val screenshotsAllowed = true
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
            id = R.id.smtpTls; setText(R.string.smtp_direct_tls)
            isChecked = DeliveryPolicy.conventionalTls(existing?.port ?: 587) ?: (existing?.directTls ?: false)
            setOnCheckedChangeListener { _, checked ->
                if (port.text.toString() in listOf("465", "587")) port.setText(if (checked) "465" else "587")
            }
            form.addView(this)
        }
        port.doAfterTextChanged { text ->
            DeliveryPolicy.conventionalTls(DeliveryPolicy.digits(text.toString()).toIntOrNull() ?: 0)?.let { tls.isChecked = it }
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
        lifecycleScope.launch {
            val last=withContext(Dispatchers.IO) { runCatching { SmtpDiagnostics.last(this@SmtpSettingsActivity,existing) }.getOrNull() }
            if(last!=null && status.text.toString()==getString(R.string.smtp_not_tested)) {
                val label=if(last.optString("result")=="OK") R.string.smtp_test_ok else runCatching { failureLabel(SmtpFailure.valueOf(last.getString("result"))) }.getOrDefault(R.string.smtp_test_failed)
                status.text=getString(label)+"\n"+getString(R.string.fs_smtp_test_time,java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last.optLong("testedAt"))))
            }
        }
        fun profile(): SmtpProfile? {
            if (!DeliveryPolicy.validHost(host.text.toString().trim())) {
                host.error = getString(R.string.smtp_host_invalid)
                status.setText(R.string.smtp_host_invalid)
                return null
            }
            host.error = null
            // Correcting the secure port/mode on the same host/account must not discard its secret.
            // A different host or account still requires explicit password entry.
            val sameServer = existing?.let { it.host.equals(host.text.toString().trim(), ignoreCase = true) &&
                it.username == user.text.toString().trim() } ?: false
            val secret = password.text.toString().ifEmpty { if (sameServer) existing?.password.orEmpty() else "" }
            val value = SmtpProfile(host.text.toString().trim(), DeliveryPolicy.digits(port.text.toString()).toIntOrNull() ?: 0,
                tls.isChecked, user.text.toString().trim(), secret, from.text.toString().trim(), DeliveryPolicy.digits(limit.text.toString()).toIntOrNull() ?: 0, existing?.cameraRecipients.orEmpty())
            if (!value.valid()) { status.setText(R.string.smtp_invalid); return null }
            return value
        }
        var busy = false
        val actions = mutableListOf<View>()
        fun setBusy(value: Boolean) {
            busy = value
            (listOf(host, port, tls, user, password, from, limit) + actions).forEach {
                it.isEnabled = !value; it.alpha = if (value) .45f else 1f
            }
        }
        DeliveryUi.button(root, R.string.smtp_save, R.id.smtpSave) {
            if (busy) return@button
            val value = profile()
            if (value != null && !busy) {
                setBusy(true)
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { store.save(value) }.isSuccess }
                    setBusy(false)
                    if (ok) { existing = value; password.text.clear(); password.setHint(R.string.smtp_password_kept) }
                    status.setText(if (ok) R.string.smtp_saved else R.string.smtp_store_failed)
                }
            }
        }.also(actions::add)
        DeliveryUi.button(root, R.string.smtp_test, R.id.smtpTest) {
            if (busy) return@button
            val value = profile()
            if (value != null && !busy) {
                setBusy(true); status.setText(R.string.smtp_testing)
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val result=SmtpSetup({ SmtpSender("", "").test(it) }, { store.save(it) }).testAndSave(value)
                        runCatching { when(result) {
                            SmtpSetup.Result.Saved -> SmtpDiagnostics.record(this@SmtpSettingsActivity,value,"OK")
                            is SmtpSetup.Result.Failed -> SmtpDiagnostics.record(this@SmtpSettingsActivity,value,result.reason.name)
                            else -> Unit
                        } }
                        result
                    }
                    when (result) {
                        is SmtpSetup.Result.Failed -> status.setText(failureLabel(result.reason))
                        SmtpSetup.Result.StorageFailed -> status.setText(R.string.smtp_test_store_failed)
                        SmtpSetup.Result.Saved -> {
                            existing = value; password.text.clear(); password.setHint(R.string.smtp_password_kept)
                            status.setText(R.string.smtp_test_ok)
                        }
                    }
                    setBusy(false)
                }
            }
        }.also(actions::add)
        DeliveryUi.button(root, R.string.mail_outbox, R.id.mailOutbox) { startActivity(Intent(this, MailOutboxActivity::class.java)) }
        DeliveryUi.button(root, R.string.smtp_forget, R.id.smtpForget) {
            if (!busy) MaterialAlertDialogBuilder(this).setMessage(R.string.smtp_forget_note)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.smtp_forget) { _, _ ->
                    store.clear(); existing = null; password.text.clear(); password.setHint(R.string.smtp_password); status.setText(R.string.smtp_forgotten)
                }.show()
        }.also(actions::add)
    }
    companion object {
        fun failureLabel(failure: SmtpFailure): Int = when (failure) {
            SmtpFailure.DNS -> R.string.smtp_error_dns
            SmtpFailure.CONNECTION -> R.string.smtp_error_connection
            SmtpFailure.TIMEOUT -> R.string.smtp_error_timeout
            SmtpFailure.TLS -> R.string.smtp_error_tls
            SmtpFailure.AUTH -> R.string.smtp_error_auth
            SmtpFailure.STARTTLS -> R.string.smtp_error_starttls
            SmtpFailure.OTHER -> R.string.smtp_test_failed
        }
    }
}
