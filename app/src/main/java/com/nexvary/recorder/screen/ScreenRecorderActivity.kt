package com.nexvary.recorder.screen

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
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
private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micOk =
            !binding.switchMic.isChecked ||
                grants[Manifest.permission.RECORD_AUDIO] != false

        val cameraOk =
            !binding.switchCamera.isChecked ||
                grants[Manifest.permission.CAMERA] != false

        when {
            !micOk -> showError(getString(R.string.permission_mic_required))
            !cameraOk -> showError(getString(R.string.camera_permission_required))
            else -> requestProjection()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFlow()
        } else {
            showError(getString(R.string.overlay_permission_required))
        }
    }

    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.System.canWrite(this)) {
            startFlow()
        } else {
            showError(getString(R.string.write_settings_permission_required))
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult

        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        runCatching {
            contentResolver.takePersistableUriPermission(uri, flags)
        }

        val folderName =
            DocumentFile.fromTreeUri(this, uri)?.name
                ?: uri.lastPathSegment
                ?: uri.toString()

        ScreenRecorderPrefs.setOutputTree(
            this,
            uri.toString(),
            folderName
        )
        updateStorageLabel()
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
            setIdleUi()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message =
                intent?.getStringExtra(ScreenRecordService.EXTRA_MESSAGE)

            when (intent?.getStringExtra(ScreenRecordService.EXTRA_STATE)) {
                ScreenRecordService.STATE_STARTING -> {
                    binding.txtStatus.text = message.orEmpty()
                }

                ScreenRecordService.STATE_STARTED -> {
                    binding.txtStatus.text =
                        message ?: getString(R.string.recording_started)
                    setRecordingUi(paused = false)
}

                ScreenRecordService.STATE_PAUSED -> {
                    binding.txtStatus.text =
                        message ?: getString(R.string.recording_paused)
                    setRecordingUi(paused = true)
                }

                ScreenRecordService.STATE_RESUMED -> {
                    binding.txtStatus.text =
                        message ?: getString(R.string.recording_resumed)
                    setRecordingUi(paused = false)
                }

                ScreenRecordService.STATE_STOPPED -> {
                    binding.txtStatus.text =
                        message ?: getString(R.string.recording_stopped)

                    val customFolder =
                        ScreenRecorderPrefs.outputTreeName(this@ScreenRecorderActivity)

                    binding.txtOutput.text =
                        if (customFolder.isNotBlank()) {
                            getString(
                                R.string.custom_save_folder_format,
                                customFolder
                            )
                        } else {
                            getString(R.string.open_gallery_hint)
                        }

                    setIdleUi()
                }

                ScreenRecordService.STATE_ERROR -> {
                    showError(
                        message ?: getString(R.string.recording_failed)
                    )
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

        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        loadPreferences()
        wireControls()

        if (ScreenRecordService.isRecording) {
            binding.txtStatus.text = getString(R.string.recording_running)
            setRecordingUi(ScreenRecordService.isPaused)
        } else {
            setIdleUi()
        }
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(ScreenRecordService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        super.onStop()
    }

    private fun wireControls() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnStart.setOnClickListener {
savePreferences()
            startFlow()
        }

        binding.btnPauseResume.setOnClickListener {
            startService(
                Intent(this, ScreenRecordService::class.java).apply {
                    action =
                        if (ScreenRecordService.isPaused) {
                            ScreenRecordService.ACTION_RESUME
                        } else {
                            ScreenRecordService.ACTION_PAUSE
                        }
                }
            )
        }

        binding.btnStop.setOnClickListener {
            startService(
                Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                }
            )
            binding.txtStatus.text = getString(R.string.stopping_saving)
        }

        binding.btnStorage.setOnClickListener {
            folderPicker.launch(null)
        }

        binding.switchFloating.setOnCheckedChangeListener { _, enabled ->
            ScreenRecorderPrefs.setFloatingControl(this, enabled)
        }

        binding.switchTouches.setOnCheckedChangeListener { _, enabled ->
            ScreenRecorderPrefs.setShowTouches(this, enabled)
        }

        binding.groupCountdown.setOnCheckedChangeListener { _, checkedId ->
            val seconds = when (checkedId) {
                R.id.radio0 -> 0
                R.id.radio5 -> 5
                R.id.radio10 -> 10
                else -> 3
            }
            ScreenRecorderPrefs.setCountdownSeconds(this, seconds)
        }
    }

    private fun loadPreferences() {
        binding.switchFloating.isChecked =
            ScreenRecorderPrefs.floatingControl(this)

        binding.switchTouches.isChecked =
            ScreenRecorderPrefs.showTouches(this)

        when (ScreenRecorderPrefs.countdownSeconds(this)) {
            0 -> binding.radio0.isChecked = true
            5 -> binding.radio5.isChecked = true
            10 -> binding.radio10.isChecked = true
            else -> binding.radio3.isChecked = true
        }

        updateStorageLabel()
    }

    private fun savePreferences() {
        ScreenRecorderPrefs.setFloatingControl(
            this,
            binding.switchFloating.isChecked
        )
        ScreenRecorderPrefs.setShowTouches(
            this,
            binding.switchTouches.isChecked
        )
    }

    private fun updateStorageLabel() {
        val name = ScreenRecorderPrefs.outputTreeName(this)

        binding.txtStorage.text =
            if (name.isBlank()) {
                getString(R.string.default_save_folder)
            } else {
                getString(R.string.custom_save_folder_format, name)
            }
    }

    private fun startFlow() {
        if (
            (binding.switchFloating.isChecked || binding.switchCamera.isChecked) &&
            !Settings.canDrawOverlays(this)
        ) {
            binding.txtStatus.text =
                getString(R.string.overlay_permission_required)

            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (
            binding.switchTouches.isChecked &&
            !Settings.System.canWrite(this)
        ) {
            binding.txtStatus.text =
                getString(R.string.write_settings_permission_required)

            writeSettingsPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        val permissions = mutableListOf<String>()

        if (
            binding.switchMic.isChecked &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.RECORD_AUDIO
        }

        if (
            binding.switchCamera.isChecked &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.CAMERA
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isNotEmpty()) {
            runtimePermissions.launch(permissions.toTypedArray())
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        binding.txtStatus.text = getString(R.string.choose_fullscreen)

        /*
         * Use Android's standard projection chooser.
         * On Android 14/15 this lets the OEM/system present its supported
         * Entire screen / single-app choices instead of forcing a mode that
         * some vendor builds reject immediately.
         */
        projectionLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )
    }

    private fun beginCountdown() {
        binding.btnStart.isEnabled = false
        binding.btnPauseResume.isEnabled = false
        binding.btnStop.isEnabled = false

        val seconds = ScreenRecorderPrefs.countdownSeconds(this)

        if (seconds <= 0) {
            startRecordingService()
            return
        }

        lifecycleScope.launch {
            for (i in seconds downTo 1) {
                binding.txtStatus.text =
                    getString(R.string.countdown_format, i)
                delay(1_000)
            }
            startRecordingService()
        }
    }

    private fun startRecordingService() {
        val resultCode =
            pendingResultCode
                ?: return showError(
                    getString(R.string.lost_screen_permission)
                )

        val resultData =
            pendingResultData
                ?: return showError(
                    getString(R.string.lost_screen_data)
                )

        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(
                    ScreenRecordService.EXTRA_RESULT_CODE,
                    resultCode
                )
                putExtra(
                    ScreenRecordService.EXTRA_RESULT_DATA,
                    resultData as Parcelable
                )
                putExtra(
                    ScreenRecordService.EXTRA_MIC,
                    binding.switchMic.isChecked
                )
                putExtra(
                    ScreenRecordService.EXTRA_FLOATING,
                    binding.switchFloating.isChecked
                )
                putExtra(
                    ScreenRecordService.EXTRA_CAMERA,
                    binding.switchCamera.isChecked
                )
                putExtra(
                    ScreenRecordService.EXTRA_SHOW_TOUCHES,
                    binding.switchTouches.isChecked
                )
                putExtra(
                    ScreenRecordService.EXTRA_OUTPUT_TREE_URI,
                    ScreenRecorderPrefs.outputTreeUri(
                        this@ScreenRecorderActivity
                    )
                )
            }
        )

        pendingResultCode = null
        pendingResultData = null

        binding.txtStatus.text =
            getString(R.string.checking_recorder)
    }

    private fun
{
        val targetPackage = pendingLaunchPackage ?: return
val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent == null) {
            showError(getString(R.string.fg_link_not_installed))
            return
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        startActivity(launchIntent)
    }

    private fun setRecordingUi(paused: Boolean) {
        binding.btnStart.isEnabled = false
        binding.btnPauseResume.isEnabled = true
        binding.btnStop.isEnabled = true
        binding.recordingIndicator.visibility = View.VISIBLE

        binding.btnPauseResume.setText(
            if (paused) {
                R.string.resume_recording
            } else {
                R.string.pause_recording
            }
        )
    }

    private fun setIdleUi() {
        binding.btnStart.isEnabled = true
        binding.btnPauseResume.isEnabled = false
        binding.btnStop.isEnabled = false
        binding.recordingIndicator.visibility = View.GONE
        binding.btnPauseResume.setText(R.string.pause_recording)
    }

    private fun showError(message: String) {
        binding.txtStatus.text = message
        binding.txtOutput.text = getString(R.string.start_failed_retry)
    }
}
