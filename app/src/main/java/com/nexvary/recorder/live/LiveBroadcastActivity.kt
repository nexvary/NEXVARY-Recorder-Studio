package com.nexvary.recorder.live

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import com.nexvary.recorder.databinding.ActivityLiveBroadcastBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets

class LiveBroadcastActivity : AppCompatActivity() {
    companion object {
        private const val FACEBOOK_LIVE_PRODUCER = "https://www.facebook.com/live/producer"
        private const val DEFAULT_FACEBOOK_SERVER = "rtmps://live-api-s.facebook.com:443/rtmp/"
    }

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
        UiInsets.apply(binding.root)

        if (binding.editServer.text.isNullOrBlank()) binding.editServer.setText(DEFAULT_FACEBOOK_SERVER)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenProducer.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FACEBOOK_LIVE_PRODUCER)))
            }.onFailure {
                setStatus(getString(R.string.cannot_open_producer))
            }
        }
        binding.btnPasteKey.setOnClickListener { pasteStreamKey() }
        binding.btnVideo.setOnClickListener { picker.launch(arrayOf("video/mp4", "video/*")) }
        binding.btnStart.setOnClickListener { startBroadcast() }
        binding.btnStop.setOnClickListener {
            startService(Intent(this, LiveBroadcastService::class.java).apply {
                action = LiveBroadcastService.ACTION_STOP
            })
            binding.txtStatus.text = getString(R.string.stop_live)
        }
        updateButtons()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LiveBroadcastService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        super.onStop()
    }

    private fun pasteStreamKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            setStatus(getString(R.string.clipboard_empty))
            return
        }
        binding.editKey.setText(text)
        binding.editKey.setSelection(text.length)
        setStatus(getString(R.string.key_pasted))
    }

    private fun startBroadcast() {
        val uri = videoUri ?: return setStatus(getString(R.string.choose_video_first))
        val server = binding.editServer.text.toString().trim()
        val key = binding.editKey.text.toString().trim()

        if (!server.startsWith("rtmps://") && !server.startsWith("rtmp://")) {
            return setStatus(getString(R.string.server_invalid))
        }
        if (key.isBlank()) return setStatus(getString(R.string.key_required))

        val endpoint = if (server.endsWith('/')) server + key else "$server/$key"
        ContextCompat.startForegroundService(
            this,
            Intent(this, LiveBroadcastService::class.java).apply {
                action = LiveBroadcastService.ACTION_START
                putExtra(LiveBroadcastService.EXTRA_URI, uri.toString())
                putExtra(LiveBroadcastService.EXTRA_ENDPOINT, endpoint)
                putExtra(LiveBroadcastService.EXTRA_LOOP, binding.checkLoop.isChecked)
            }
        )
        setStatus(getString(R.string.sending_facebook))
        updateButtons()
    }

    private fun updateButtons() {
        binding.btnStart.isEnabled = !LiveBroadcastService.isRunning
        binding.btnStop.isEnabled = LiveBroadcastService.isRunning
    }

    private fun setStatus(text: String) {
        binding.txtStatus.text = text
    }
}
