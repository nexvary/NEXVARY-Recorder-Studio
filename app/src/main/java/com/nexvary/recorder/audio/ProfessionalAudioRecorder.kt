package com.nexvary.recorder.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class ProfessionalAudioRecorder(private val context: Context, private val onCaptureError: (() -> Unit)? = null) {
    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 1
    }

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private var rawFile: File? = null
    private val effects = mutableListOf<android.media.audiofx.AudioEffect>()
    @Volatile var captureFailed = false
        private set

    fun start(): File {
        if (running.get()) error("Already recording")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission is required")
        }
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(min > 0) { "Audio capture unsupported" }
        val bufferBytes = max(min * 4, SAMPLE_RATE / 2)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        val session = audioRecord!!.audioSessionId
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { effects.add(it); it.enabled = true } }
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.let { effects.add(it); it.enabled = true } }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { effects.add(it); it.enabled = true } }

        rawFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.pcm")
        captureFailed = false
        try {
            check(audioRecord!!.state == AudioRecord.STATE_INITIALIZED) { "Audio capture unavailable" }
            running.set(true)
            audioRecord!!.startRecording()
            thread = Thread({ captureLoop(bufferBytes / 2) }, "NexvaryVoiceCapture").apply { start() }
        } catch (error: Exception) {
            running.set(false); audioRecord?.release(); audioRecord = null
            effects.forEach { runCatching { it.release() } }; effects.clear()
            rawFile?.delete(); rawFile = null
            throw error
        }
        return rawFile!!
    }

    private fun captureLoop(bufferSamples: Int) {
        try {
            val samples = ShortArray(bufferSamples)
            BufferedOutputStream(FileOutputStream(rawFile!!)).use { out ->
                val buffer = ByteBuffer.allocate(bufferSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
                while (running.get()) {
                    val count = audioRecord?.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING) ?: break
                    if (count < 0) { if (running.get()) error("Audio capture interrupted") else break }
                    if (count > 0) {
                        buffer.clear()
                        for (i in 0 until count) buffer.putShort(samples[i])
                        out.write(buffer.array(), 0, count * 2)
                    }
                }
            }
        } catch (_: Exception) {
            captureFailed = true; running.set(false); onCaptureError?.invoke()
        }
    }

    fun stopAndEnhance(destinationWav: File): File {
        check(audioRecord != null) { "Not recording" }
        running.set(false)
        runCatching { audioRecord?.stop() }
        thread?.join(2500)
        audioRecord?.release(); audioRecord = null
        effects.forEach { runCatching { it.release() } }; effects.clear()
        val capture = thread
        if (capture?.isAlive == true) capture.join(1000)
        check(capture?.isAlive != true) { "Audio capture did not stop" }
        thread = null
        val input = rawFile ?: error("No raw recording")
        PcmEnhancer.enhance(input, destinationWav, SAMPLE_RATE)
        input.delete(); rawFile = null
        return destinationWav
    }
}
