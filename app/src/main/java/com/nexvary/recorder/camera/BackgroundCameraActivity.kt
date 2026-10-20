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
import androidx.lifecycle.lifecycleScope
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
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
        val timing = DeliveryUi.section(root, R.string.camera_timer_title)
        timer = switch(timing, R.string.camera_timer_enable, R.id.cameraTimer, "timer")
        minutes = DeliveryUi.field(timing, R.string.camera_minutes, R.id.cameraMinutes, savedInstanceState?.getString("minutes") ?: "10", InputType.TYPE_CLASS_NUMBER)
        minutes.isEnabled = timer.isChecked
        timer.setOnCheckedChangeListener { _, checked -> minutes.isEnabled = checked }
        DeliveryUi.text(timing, R.string.camera_timer_note)
        val delivery = DeliveryUi.section(root, R.string.camera_email_title)
        email = switch(delivery, R.string.camera_email_enable, R.id.cameraEmail, "email")
        val prefs = getSharedPreferences("camera_options", MODE_PRIVATE)
        recipient = DeliveryUi.field(delivery, R.string.camera_recipient, R.id.cameraRecipient,
            savedInstanceState?.getString("recipient") ?: prefs.getString("recipient", "").orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        recipient.isEnabled = email.isChecked
        email.setOnCheckedChangeListener { _, checked -> recipient.isEnabled = checked }
        DeliveryUi.text(delivery, R.string.camera_email_note)
        DeliveryUi.button(delivery, R.string.smtp_title, R.id.openSmtp) { startActivity(Intent(this, SmtpSettingsActivity::class.java)) }
        DeliveryUi.button(delivery, R.string.mail_outbox, R.id.mailOutbox) { startActivity(Intent(this, MailOutboxActivity::class.java)) }
        status = DeliveryUi.text(root, R.string.camera_ready).apply { id = R.id.cameraStatus; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        start = DeliveryUi.button(root, R.string.camera_start, R.id.cameraStart) { requestAndStart() }
        stop = DeliveryUi.button(root, R.string.camera_stop, R.id.cameraStop, red = true) {
            startService(Intent(this, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
        }
    }
    override fun onStart() {
        super.onStart(); handler.post(tick)
        lifecycleScope.launch { withContext(Dispatchers.IO) { runCatching { MailWorker.reconcile(this@BackgroundCameraActivity) } } }
    }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }
    private fun updateState() {
        val state = BackgroundCameraService.state
        val active = state.stage in listOf(BackgroundCameraService.Companion.Stage.STARTING, BackgroundCameraService.Companion.Stage.RECORDING, BackgroundCameraService.Companion.Stage.SAVING)
        start.isEnabled = !active; stop.isEnabled = active && state.stage != BackgroundCameraService.Companion.Stage.SAVING
        start.alpha = if (start.isEnabled) 1f else 0.45f
        stop.alpha = if (stop.isEnabled) 1f else 0.45f
        front.isEnabled = !active; audio.isEnabled = !active; timer.isEnabled = !active; email.isEnabled = !active
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
        super.onSaveInstanceState(outState)
    }
    private fun requestAndStart() {
        val duration = if (timer.isChecked) DeliveryPolicy.duration(minutes.text.toString()) else 0L
        if (duration == null) { minutes.error = getString(R.string.camera_minutes_invalid); status.setText(R.string.camera_minutes_invalid); return }
        val to = if (email.isChecked) recipient.text.toString().trim() else ""
        if (email.isChecked && !DeliveryPolicy.email(to)) { recipient.error = getString(R.string.camera_recipient_invalid); status.setText(R.string.camera_recipient_invalid); return }
        if (email.isChecked && SmtpSettings(this).load() == null) { status.setText(R.string.mail_config); return }
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (audio.isChecked) permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { notificationAsked = true; pendingStart = true; requestPermissions(missing.toTypedArray(), 600); return }
        pendingStart = false
        if (email.isChecked) getSharedPreferences("camera_options", MODE_PRIVATE).edit().putString("recipient", to).apply()
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                .putExtra(BackgroundCameraService.EXTRA_FRONT, front.isChecked).putExtra(BackgroundCameraService.EXTRA_AUDIO, audio.isChecked)
                .putExtra(BackgroundCameraService.EXTRA_DURATION, duration).putExtra(BackgroundCameraService.EXTRA_RECIPIENT, to))
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
