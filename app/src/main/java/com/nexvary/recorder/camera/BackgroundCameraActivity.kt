package com.nexvary.recorder.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.family.GuardianGate
import androidx.lifecycle.Lifecycle
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgroundCameraActivity : AppCompatActivity() {
    private lateinit var front: SwitchCompat
    private lateinit var audio: SwitchCompat
    private lateinit var timer: SwitchCompat
    private lateinit var email: SwitchCompat
    private lateinit var minutes: EditText
    private lateinit var recipient: EditText
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var qualityGroup: RadioGroup
    private lateinit var qualityEstimate: TextView
    private var smtpLimitMiB: Int? = null
    private var reviewingMail = false
    private fun selectedQuality() = when (qualityGroup.checkedRadioButtonId) {
        R.id.cameraQualityBalanced -> CameraQuality.BALANCED
        R.id.cameraQualityHigh -> CameraQuality.HIGH
        else -> CameraQuality.COMPACT
    }
    private var pendingStart = false
    private var notificationAsked = false
    private var lastState: BackgroundCameraService.Companion.CaptureState? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { updateState(); handler.postDelayed(this, 1000) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        notificationAsked = savedInstanceState?.getBoolean("notification_asked") ?: false
        val root = com.nexvary.recorder.ui.StudioPage.install(this, R.string.camera_title)
        DeliveryUi.text(root, R.string.camera_note)
        fun switch(parent: LinearLayout, label: Int, ident: Int, saved: String): SwitchCompat = SwitchCompat(this).apply {
            id = ident; setText(label); isChecked = savedInstanceState?.getBoolean(saved) ?: false; parent.addView(this)
        }
        front = switch(root, R.string.camera_front, R.id.cameraFront, "front")
        audio = switch(root, R.string.camera_mic, R.id.cameraMic, "audio")
        val prefs = getSharedPreferences("camera_options", MODE_PRIVATE)
        val qualityPanel = DeliveryUi.section(root, R.string.camera_quality_title)
        qualityGroup = RadioGroup(this).apply { id = R.id.cameraQualityGroup; qualityPanel.addView(this) }
        val initialQuality = CameraQuality.fromKey(savedInstanceState?.getString("quality") ?: prefs.getString("quality", null))
        listOf(Triple(CameraQuality.COMPACT, R.id.cameraQualityCompact, R.string.camera_quality_compact),
            Triple(CameraQuality.BALANCED, R.id.cameraQualityBalanced, R.string.camera_quality_balanced),
            Triple(CameraQuality.HIGH, R.id.cameraQualityHigh, R.string.camera_quality_high)).forEach { (quality, ident, label) ->
            qualityGroup.addView(androidx.appcompat.widget.AppCompatRadioButton(this).apply {
                id = ident; setText(label); textSize = 15f
                minHeight = (52 * resources.displayMetrics.density).toInt()
                setTextColor(0xFF43FF88.toInt()); buttonTintList = android.content.res.ColorStateList.valueOf(0xFF43FF88.toInt())
                setTag(R.id.studioStyled, true)
            })
            if (quality == initialQuality) qualityGroup.check(ident)
        }
        qualityEstimate = DeliveryUi.text(qualityPanel, R.string.camera_quality_note).apply { id = R.id.cameraQualityEstimate }
        DeliveryUi.text(qualityPanel, R.string.camera_quality_note)
        val timing = DeliveryUi.section(root, R.string.camera_timer_title)
        timer = switch(timing, R.string.camera_timer_enable, R.id.cameraTimer, "timer")
        minutes = DeliveryUi.field(timing, R.string.camera_minutes, R.id.cameraMinutes, savedInstanceState?.getString("minutes") ?: "10", InputType.TYPE_CLASS_NUMBER)
        minutes.isEnabled = timer.isChecked
        DeliveryUi.text(timing, R.string.camera_timer_note)
        val delivery = DeliveryUi.section(root, R.string.camera_email_title)
        email = switch(delivery, R.string.camera_email_enable, R.id.cameraEmail, "email")
        recipient = DeliveryUi.field(delivery, R.string.camera_recipients_label, R.id.cameraRecipient,
            savedInstanceState?.getString("recipient") ?: prefs.getString("recipient", "").orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            setSingleLine(false); minLines = 2; maxLines = 6
            textDirection = View.TEXT_DIRECTION_LTR
            filters = arrayOf(android.text.InputFilter.LengthFilter(MailRecipients.MAX_INPUT))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        recipient.isEnabled = email.isChecked
        DeliveryUi.text(delivery, R.string.camera_recipients_hint)

        DeliveryUi.text(delivery, R.string.camera_email_note)
        DeliveryUi.text(delivery, R.string.camera_multi_policy)
        DeliveryUi.button(delivery, R.string.smtp_title, R.id.openSmtp) { startActivity(Intent(this, SmtpSettingsActivity::class.java)) }
        DeliveryUi.button(delivery, R.string.mail_outbox, R.id.mailOutbox) { startActivity(Intent(this, MailOutboxActivity::class.java)) }
        status = DeliveryUi.text(root, R.string.camera_ready).apply { id = R.id.cameraStatus; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        start = DeliveryUi.button(root, R.string.camera_start, R.id.cameraStart) { requestAndStart() }
        stop = DeliveryUi.button(root, R.string.camera_stop, R.id.cameraStop, red = true) {
            startService(Intent(this, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
        }
        qualityGroup.setOnCheckedChangeListener { _, _ ->
            prefs.edit().putString("quality", selectedQuality().key).apply(); updateEstimate()
        }
        audio.setOnCheckedChangeListener { _, _ -> updateEstimate() }
        timer.setOnCheckedChangeListener { _, checked -> minutes.isEnabled = checked; updateEstimate() }
        email.setOnCheckedChangeListener { _, checked -> recipient.isEnabled = checked; updateEstimate() }
        minutes.doAfterTextChanged { updateEstimate() }
        updateEstimate()
    }
    override fun onStart() {
        super.onStart(); handler.post(tick)
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching { MailWorker.reconcile(this@BackgroundCameraActivity) }
                SmtpSettings(this@BackgroundCameraActivity).load()
            }
            smtpLimitMiB = profile?.limitMiB
            if (recipient.text.isBlank() && !profile?.cameraRecipients.isNullOrEmpty()) recipient.setText(profile?.cameraRecipients)
            updateEstimate()
        }
    }
    private fun updateEstimate() {
        val quality = selectedQuality()
        fun mb(bytes: Long) = String.format(resources.configuration.locales[0], "%.1f", bytes / 1_000_000.0)
        val five = quality.estimatedBytes(300_000, audio.isChecked)
        val lines = mutableListOf(getString(R.string.camera_quality_estimate, mb(quality.estimatedBytes(60_000, audio.isChecked)),
            mb(five), mb(DeliveryPolicy.messageBytes(five))))
        var warning = false
        if (email.isChecked) {
            val duration = if (timer.isChecked) DeliveryPolicy.duration(minutes.text.toString()) else null
            if (duration != null) lines.add(getString(R.string.camera_quality_timed_size, mb(quality.estimatedBytes(duration, audio.isChecked)),
                mb(DeliveryPolicy.messageBytes(quality.estimatedBytes(duration, audio.isChecked)))))
            else lines.add(getString(R.string.camera_quality_unbounded))
            smtpLimitMiB?.let { limit ->
                lines.add(getString(R.string.camera_quality_limit, limit))
                warning = !DeliveryPolicy.fits(quality.budgetBytes(duration ?: 300_000, audio.isChecked), limit)
                if (warning) lines.add(getString(R.string.camera_quality_over_limit))
            }
        }
        qualityEstimate.text = lines.joinToString("\n")
        qualityEstimate.setTextColor(if (warning) 0xFFFF6377.toInt() else 0xFFE2E8F0.toInt())
    }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }
    private fun updateState() {
        val state = BackgroundCameraService.state
        val active = state.stage in listOf(BackgroundCameraService.Companion.Stage.STARTING, BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.Companion.Stage.SAVING)
        start.isEnabled = !active; stop.isEnabled = active && state.stage != BackgroundCameraService.Companion.Stage.SAVING
        start.alpha = if (start.isEnabled) 1f else 0.45f
        stop.alpha = if (stop.isEnabled) 1f else 0.45f
        front.isEnabled = !active; audio.isEnabled = !active; timer.isEnabled = !active; email.isEnabled = !active
        for (i in 0 until qualityGroup.childCount) qualityGroup.getChildAt(i).isEnabled = !active
        minutes.isEnabled = !active && timer.isChecked; recipient.isEnabled = !active && email.isChecked
        if (active || state != lastState) status.text = when (state.stage) {
            BackgroundCameraService.Companion.Stage.IDLE -> getString(R.string.camera_ready)
            BackgroundCameraService.Companion.Stage.STARTING -> getString(R.string.camera_starting)
            BackgroundCameraService.Companion.Stage.SAVING -> getString(R.string.camera_saving)
            BackgroundCameraService.Companion.Stage.ERROR -> getString(R.string.camera_failed)
            BackgroundCameraService.Companion.Stage.SAVED -> getString(if (state.mailQueued) R.string.camera_saved_mail else if (state.mailRequested) R.string.camera_saved_mail_failed else R.string.camera_saved, state.name)
            BackgroundCameraService.Companion.Stage.RECORDING -> if (state.deadline == 0L) getString(R.string.camera_recording)
                else getString(R.string.camera_remaining, ((state.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000)
        }
        lastState = state
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_asked", notificationAsked)
        outState.putBoolean("front", front.isChecked); outState.putBoolean("audio", audio.isChecked)
        outState.putBoolean("timer", timer.isChecked); outState.putBoolean("email", email.isChecked)
        outState.putString("minutes", minutes.text.toString()); outState.putString("recipient", recipient.text.toString())
        outState.putString("quality", selectedQuality().key)
        super.onSaveInstanceState(outState)
    }
    private fun requestAndStart() {
        val duration = if (timer.isChecked) DeliveryPolicy.duration(minutes.text.toString()) else 0L
        if (duration == null) { minutes.error = getString(R.string.camera_minutes_invalid); status.setText(R.string.camera_minutes_invalid); return }
        val to = if (email.isChecked) MailRecipients.canonical(recipient.text.toString()) else ""
        if (to == null) { recipient.error = getString(R.string.camera_recipients_invalid); status.setText(R.string.camera_recipients_invalid); return }
        if (email.isChecked && SmtpSettings(this).load() == null) { status.setText(R.string.mail_config); return }
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (audio.isChecked) permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { notificationAsked = true; pendingStart = true; requestPermissions(missing.toTypedArray(), 600); return }
        pendingStart = false
        if (email.isChecked) {
            GuardianGate.action(this) {
                if (reviewingMail || isFinishing) return@action
                reviewingMail = true
                val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.camera_mail_review_title)
                    .setMessage(getString(R.string.camera_mail_review, to.split(", ").joinToString("\n")))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.camera_mail_confirm) { _, _ ->
                        lifecycleScope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    val settings = SmtpSettings(this@BackgroundCameraActivity)
                                    val profile = checkNotNull(settings.load())
                                    settings.save(profile.copy(cameraRecipients = to))
                                    getSharedPreferences("camera_options", MODE_PRIVATE).edit().remove("recipient").commit()
                                }.isSuccess
                            }
                            if (!saved) status.setText(R.string.smtp_store_failed)
                            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) beginCapture(duration, to)
                        }
                    }.create()
                dialog.setOnDismissListener { reviewingMail = false }
                dialog.show()
            }
        } else beginCapture(duration, "")
    }
    private fun beginCapture(duration: Long, to: String) {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                .putExtra(BackgroundCameraService.EXTRA_FRONT, front.isChecked).putExtra(BackgroundCameraService.EXTRA_AUDIO, audio.isChecked)
                .putExtra(BackgroundCameraService.EXTRA_DURATION, duration).putExtra(BackgroundCameraService.EXTRA_RECIPIENT, to)
                .putExtra(BackgroundCameraService.EXTRA_QUALITY, selectedQuality().key))
        }.onFailure { status.setText(R.string.camera_failed) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 600 && pendingStart) {
            pendingStart = false
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                (!audio.isChecked || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) requestAndStart()
            else status.setText(R.string.camera_permission_denied)
        }
    }
}
