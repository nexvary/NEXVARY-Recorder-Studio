package com.nexvary.recorder.live

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexvary.recorder.databinding.ActivityLiveBroadcastBinding
import com.nexvary.recorder.ui.ThemeManager

class LiveBroadcastActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLiveBroadcastBinding
    private var videoUri: Uri? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        videoUri = uri
        binding.txtVideo.text = uri.lastPathSegment ?: uri.toString()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            binding.txtStatus.text = intent?.getStringExtra(LiveBroadcastService.EXTRA_STATUS).orEmpty()
            updateButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBroadcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnVideo.setOnClickListener { picker.launch(arrayOf("video/mp4", "video/*")) }
        binding.btnStart.setOnClickListener { startBroadcast() }
        binding.btnStop.setOnClickListener {
            startService(Intent(this, LiveBroadcastService::class.java).apply { action = LiveBroadcastService.ACTION_STOP })
            binding.txtStatus.text = "إيقاف البث…"
        }
        updateButtons()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LiveBroadcastService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerLegacyReceiver(filter)
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyReceiver(filter: IntentFilter) {
        registerReceiver(statusReceiver, filter)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        super.onStop()
    }

    private fun startBroadcast() {
        val uri = videoUri ?: return setStatus("اختر فيديو MP4 أولاً")
        val server = binding.editServer.text.toString().trim()
        val key = binding.editKey.text.toString().trim()
        if (!server.startsWith("rtmps://") && !server.startsWith("rtmp://")) return setStatus("أدخل Server URL يبدأ بـ rtmps://")
        if (key.isBlank()) return setStatus("أدخل Stream Key")
        val endpoint = if (server.endsWith('/')) server + key else "$server/$key"
        ContextCompat.startForegroundService(this, Intent(this, LiveBroadcastService::class.java).apply {
            action = LiveBroadcastService.ACTION_START
            putExtra(LiveBroadcastService.EXTRA_URI, uri.toString())
            putExtra(LiveBroadcastService.EXTRA_ENDPOINT, endpoint)
            putExtra(LiveBroadcastService.EXTRA_LOOP, binding.checkLoop.isChecked)
        })
        setStatus("إرسال أمر بدء البث…")
        updateButtons()
    }

    private fun updateButtons() {
        binding.btnStart.isEnabled = !LiveBroadcastService.isRunning
        binding.btnStop.isEnabled = LiveBroadcastService.isRunning
    }

    private fun setStatus(text: String) { binding.txtStatus.text = text }
}
