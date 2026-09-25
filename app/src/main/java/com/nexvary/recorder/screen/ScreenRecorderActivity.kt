package com.nexvary.recorder.screen

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexvary.recorder.R
import com.nexvary.recorder.databinding.ActivityScreenRecorderBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenRecorderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScreenRecorderBinding
    private lateinit var projectionManager: MediaProjectionManager
    private var pendingResultCode: Int? = null
    private var pendingResultData: Intent? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] != false
        if (audioOk) requestProjection()
        else showError(getString(R.string.permission_mic_required))
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pendingResultCode = result.resultCode
            pendingResultData = result.data
            beginCountdown()
        } else {
            showError(getString(R.string.permission_screen_cancelled))
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(ScreenRecordService.EXTRA_MESSAGE)
            when (intent?.getStringExtra(ScreenRecordService.EXTRA_STATE)) {
                ScreenRecordService.STATE_STARTING -> binding.txtStatus.text = message.orEmpty()
                ScreenRecordService.STATE_STARTED -> {
                    binding.txtStatus.text = message ?: getString(R.string.recording_started)
                    binding.btnStart.isEnabled = false
                    binding.btnStop.isEnabled = true
                    binding.recordingIndicator.visibility = View.VISIBLE
                    lifecycleScope.launch {
                        delay(450)
                        moveTaskToBack(true)
                    }
                }
                ScreenRecordService.STATE_STOPPED -> {
                    binding.txtStatus.text = message ?: getString(R.string.recording_stopped)
                    binding.txtOutput.text = getString(R.string.open_gallery_hint)
                    setIdleUi()
                }
                ScreenRecordService.STATE_ERROR -> {
                    showError(message ?: getString(R.string.recording_failed))
                    setIdleUi()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityScreenRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { startFlow() }
        binding.btnStop.setOnClickListener {
            startService(Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            })
            binding.txtStatus.text = getString(R.string.stopping_saving)
        }

        if (ScreenRecordService.isRecording) {
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            binding.recordingIndicator.visibility = View.VISIBLE
            binding.txtStatus.text = getString(R.string.recording_running)
        } else {
            setIdleUi()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ScreenRecordService.ACTION_STATUS)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        super.onStop()
    }

    private fun startFlow() {
        val permissions = mutableListOf<String>()
        if (binding.checkMic.isChecked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
        else requestProjection()
    }

    private fun requestProjection() {
        binding.txtStatus.text = getString(R.string.choose_fullscreen)
        val captureIntent = if (Build.VERSION.SDK_INT >= 34) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }

    private fun beginCountdown() {
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = false
        lifecycleScope.launch {
            for (i in 3 downTo 1) {
                binding.txtStatus.text = getString(R.string.countdown_format, i)
                delay(650)
            }
            startRecordingService()
        }
    }

    private fun startRecordingService() {
        val resultCode = pendingResultCode ?: return showError(getString(R.string.lost_screen_permission))
        val resultData = pendingResultData ?: return showError(getString(R.string.lost_screen_data))

        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData as Parcelable)
                putExtra(ScreenRecordService.EXTRA_MIC, binding.checkMic.isChecked)
            }
        )
        pendingResultCode = null
        pendingResultData = null
        binding.txtStatus.text = getString(R.string.checking_recorder)
    }

    private fun setIdleUi() {
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.recordingIndicator.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.txtStatus.text = message
        binding.txtOutput.text = getString(R.string.start_failed_retry)
    }
}
