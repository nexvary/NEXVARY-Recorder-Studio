package com.nexvary.recorder.audio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import com.nexvary.recorder.databinding.ActivityVoiceRecorderBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class VoiceRecorderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVoiceRecorderBinding
    private var recorder: ProfessionalAudioRecorder? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else binding.txtStatus.text = getString(R.string.permission_mic_required)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
        binding.btnStop.setOnClickListener { finishRecording() }
    }

    private fun begin() {
        try {
            recorder = ProfessionalAudioRecorder(this).also { it.start() }
            binding.btnRecord.isEnabled = false
            binding.btnStop.isEnabled = true
            binding.txtStatus.text = getString(R.string.voice_recording)
        } catch (e: Exception) {
            binding.txtStatus.text = getString(R.string.voice_start_failed_format, e.message.orEmpty())
        }
    }

    private fun finishRecording() {
        binding.btnStop.isEnabled = false
        binding.txtStatus.text = getString(R.string.voice_enhancing)
        val worker = recorder ?: return
        thread {
            try {
                val temp = File(cacheDir, "enhanced_${System.currentTimeMillis()}.wav")
                worker.stopAndEnhance(temp)
                val name = "voice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/NEXVARY Recorder")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create output")
                contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                temp.delete()
                runOnUiThread {
                    binding.btnRecord.isEnabled = true
                    binding.txtStatus.text = getString(R.string.voice_complete)
                    binding.txtOutput.text = getString(R.string.voice_saved_format, name)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.btnRecord.isEnabled = true
                    binding.txtStatus.text = getString(R.string.voice_save_failed_format, e.message.orEmpty())
                }
            } finally {
                recorder = null
            }
        }
    }
}
