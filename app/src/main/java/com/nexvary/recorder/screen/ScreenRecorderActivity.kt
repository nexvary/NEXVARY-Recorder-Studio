package com.nexvary.recorder.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexvary.recorder.databinding.ActivityScreenRecorderBinding

class ScreenRecorderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScreenRecorderBinding
    private lateinit var projectionManager: MediaProjectionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] != false
        if (audioOk) requestProjection() else binding.txtStatus.text = "يلزم إذن الميكروفون عند تفعيل تسجيل الصوت"
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val service = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecordService.EXTRA_MIC, binding.checkMic.isChecked)
            }
            ContextCompat.startForegroundService(this, service)
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            binding.txtStatus.text = "جارٍ التسجيل…"
        } else {
            binding.txtStatus.text = "تم إلغاء إذن تسجيل الشاشة"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { startFlow() }
        binding.btnStop.setOnClickListener {
            startService(Intent(this, ScreenRecordService::class.java).apply { action = ScreenRecordService.ACTION_STOP })
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            binding.txtStatus.text = "تم إيقاف التسجيل وحفظه في Movies/NEXVARY Recorder"
            binding.txtOutput.text = "افتح المعرض أو مدير الملفات للوصول إلى التسجيل."
        }
    }

    private fun startFlow() {
        val permissions = mutableListOf<String>()
        if (binding.checkMic.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray()) else requestProjection()
    }

    private fun requestProjection() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
