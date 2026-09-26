package com.nexvary.recorder.media

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.R
import com.nexvary.recorder.databinding.ActivityReplaceAudioBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class AudioReplaceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReplaceAudioBinding
    private var videoUri: Uri? = null
    private var audioUri: Uri? = null

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        videoUri = uri
        binding.txtVideo.text = uri.lastPathSegment ?: uri.toString()
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        audioUri = uri
        binding.txtAudio.text = uri.lastPathSegment ?: uri.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityReplaceAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnVideo.setOnClickListener { videoPicker.launch(arrayOf("video/*")) }
        binding.btnAudio.setOnClickListener { audioPicker.launch(arrayOf("audio/*")) }
        binding.btnMerge.setOnClickListener { merge() }
    }

    private fun merge() {
        val v = videoUri ?: return show(getString(R.string.choose_video_first))
        val a = audioUri ?: return show(getString(R.string.choose_audio_first))
        binding.btnMerge.isEnabled = false
        show(getString(R.string.creating_video))
        thread {
            var outputUri: Uri? = null
            try {
                val name = "dubbed_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NEXVARY Recorder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create output")
                contentResolver.openFileDescriptor(outputUri, "w")!!.use { pfd ->
                    MediaMuxerUtil.replaceAudio(this, v, a, pfd.fileDescriptor, cacheDir)
                }
                contentResolver.update(outputUri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
                runOnUiThread { show(getString(R.string.video_saved_format, name)) }
            } catch (e: Exception) {
                outputUri?.let { contentResolver.delete(it, null, null) }
                runOnUiThread { show(getString(R.string.video_create_failed_format, e.message.orEmpty())) }
            } finally {
                runOnUiThread { binding.btnMerge.isEnabled = true }
            }
        }
    }

    private fun show(msg: String) {
        binding.txtStatus.text = msg
    }
}
