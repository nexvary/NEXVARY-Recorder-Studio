package com.ibmempire.recorder.media

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ibmempire.recorder.R
import com.ibmempire.recorder.databinding.ActivityMediaToolsBinding
import com.ibmempire.recorder.ui.ThemeManager
import com.ibmempire.recorder.ui.UiInsets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MediaToolsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaToolsBinding
    private var audioUri: Uri? = null
    private var videoUri: Uri? = null

    private val audioTargets = listOf(
        AudioFormatConverter.Target.WAV,
        AudioFormatConverter.Target.M4A,
        AudioFormatConverter.Target.OGG
    )

    private val videoTargets = listOf(
        VideoToolsEngine.Container.MP4,
        VideoToolsEngine.Container.THREE_GPP,
        VideoToolsEngine.Container.WEBM
    )

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        persistRead(uri)
        audioUri = uri
        binding.txtAudioFile.text =
            uri.lastPathSegment ?: uri.toString()
    }

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        persistRead(uri)
        videoUri = uri
        binding.txtVideoFile.text =
            uri.lastPathSegment ?: uri.toString()
        updateVideoDuration(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMediaToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        configureSpinners()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReplaceAudio.setOnClickListener {
            startActivity(Intent(this, AudioReplaceActivity::class.java))
        }
        binding.btnChooseAudio.setOnClickListener {
            audioPicker.launch(arrayOf("audio/*"))
        }
        binding.btnConvertAudio.setOnClickListener {
            convertAudio()
        }
        binding.btnChooseVideo.setOnClickListener {
            videoPicker.launch(arrayOf("video/*"))
        }
        binding.btnTrimVideo.setOnClickListener {
            trimVideo()
        }
        binding.btnConvertVideo.setOnClickListener {
            convertVideo()
        }
    }

    private fun configureSpinners() {
        val audioLabels = listOf(
            getString(R.string.audio_format_wav),
            getString(R.string.audio_format_m4a),
            getString(R.string.audio_format_ogg)
        )
        binding.spinnerAudioFormat.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                audioLabels
            )

        val videoLabels = listOf(
            getString(R.string.video_format_mp4),
            getString(R.string.video_format_3gp),
            getString(R.string.video_format_webm)
        )
        binding.spinnerVideoFormat.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                videoLabels
            )
    }

    private fun convertAudio() {
        val source =
            audioUri
                ?: return showAudio(
                    getString(R.string.choose_audio_first)
                )

        val target =
            audioTargets[binding.spinnerAudioFormat.selectedItemPosition]

        setBusy(true)
        showAudio(getString(R.string.converting_audio))

        thread {
            val temp = File(
                cacheDir,
                "converted_audio_${System.nanoTime()}.${target.extension}"
            )

            try {
                AudioFormatConverter.convert(
                    context = this,
                    input = source,
                    target = target,
                    output = temp,
                    workDir = cacheDir
                )

                val name =
                    "audio_${timestamp()}.${target.extension}"

                publishAudio(temp, name, target.mime)

                runOnUiThread {
                    showAudio(
                        getString(
                            R.string.audio_saved_format,
                            name
                        )
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    showAudio(
                        getString(
                            R.string.media_operation_failed_format,
                            error.message.orEmpty()
                        )
                    )
                }
            } finally {
                temp.delete()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun trimVideo() {
        val source =
            videoUri
                ?: return showVideo(
                    getString(R.string.choose_video_first)
                )

        val startSeconds =
            binding.inputTrimStart.text
                ?.toString()
                ?.trim()
                ?.toDoubleOrNull()
                ?: 0.0

        val endSeconds =
            binding.inputTrimEnd.text
                ?.toString()
                ?.trim()
                ?.toDoubleOrNull()
                ?: return showVideo(
                    getString(R.string.enter_trim_end)
                )

        val startMs = (startSeconds * 1_000.0).toLong()
        val endMs = (endSeconds * 1_000.0).toLong()

        if (startMs < 0L || endMs <= startMs) {
            return showVideo(
                getString(R.string.invalid_trim_range)
            )
        }

        setBusy(true)
        showVideo(getString(R.string.trimming_video))

        thread {
            val temp =
                File(cacheDir, "trim_${System.nanoTime()}.mp4")

            try {
                val result = VideoToolsEngine.trim(
                    context = this,
                    input = source,
                    output = temp,
                    startMs = startMs,
                    endMs = endMs
                )

                val name = "trimmed_${timestamp()}.mp4"
                publishVideo(temp, name, "video/mp4")

                runOnUiThread {
                    val delta =
                        kotlin.math.abs(
                            result.actualStartMs -
                                result.requestedStartMs
                        )

                    showVideo(
                        if (delta > 250L) {
                            getString(
                                R.string.video_trimmed_keyframe_format,
                                name,
                                result.actualStartMs / 1_000.0
                            )
                        } else {
                            getString(
                                R.string.video_saved_format,
                                name
                            )
                        }
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    showVideo(
                        getString(
                            R.string.media_operation_failed_format,
                            error.message.orEmpty()
                        )
                    )
                }
            } finally {
                temp.delete()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun convertVideo() {
        val source =
            videoUri
                ?: return showVideo(
                    getString(R.string.choose_video_first)
                )

        val target =
            videoTargets[binding.spinnerVideoFormat.selectedItemPosition]

        setBusy(true)
        showVideo(getString(R.string.converting_video))

        thread {
            val temp = File(
                cacheDir,
                "converted_video_${System.nanoTime()}.${target.extension}"
            )

            try {
                VideoToolsEngine.convertContainer(
                    context = this,
                    input = source,
                    output = temp,
                    target = target
                )

                val name =
                    "video_${timestamp()}.${target.extension}"

                publishVideo(temp, name, target.mime)

                runOnUiThread {
                    showVideo(
                        getString(
                            R.string.video_saved_format,
                            name
                        )
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    showVideo(
                        getString(
                            R.string.media_operation_failed_format,
                            error.message.orEmpty()
                        )
                    )
                }
            } finally {
                temp.delete()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun updateVideoDuration(uri: Uri) {
        val retriever = MediaMetadataRetriever()

        try {
            contentResolver.openAssetFileDescriptor(uri, "r")!!.use { afd ->
                if (afd.length >= 0) {
                    retriever.setDataSource(
                        afd.fileDescriptor,
                        afd.startOffset,
                        afd.length
                    )
                } else {
                    retriever.setDataSource(afd.fileDescriptor)
                }
            }

            val durationMs =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()

            if (durationMs != null && durationMs > 0L) {
                binding.inputTrimStart.setText("0")
                binding.inputTrimEnd.setText(
                    String.format(
                        Locale.US,
                        "%.2f",
                        durationMs / 1_000.0
                    )
                )
            }
        } catch (_: Exception) {
            Unit
        } finally {
            retriever.release()
        }
    }

    private fun publishAudio(
        source: File,
        name: String,
        mime: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC +
                    "/IBM Empire Recorder"
            )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri =
            contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("Unable to create audio output")

        try {
            contentResolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                },
                null,
                null
            )
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun publishVideo(
        source: File,
        name: String,
        mime: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES +
                    "/IBM Empire Recorder"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri =
            contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("Unable to create video output")

        try {
            contentResolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                },
                null,
                null
            )
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun persistRead(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

    private fun setBusy(busy: Boolean) {
        binding.btnConvertAudio.isEnabled = !busy
        binding.btnTrimVideo.isEnabled = !busy
        binding.btnConvertVideo.isEnabled = !busy
        binding.btnChooseAudio.isEnabled = !busy
        binding.btnChooseVideo.isEnabled = !busy
    }

    private fun showAudio(message: String) {
        binding.txtAudioStatus.text = message
    }

    private fun showVideo(message: String) {
        binding.txtVideoStatus.text = message
    }
}
