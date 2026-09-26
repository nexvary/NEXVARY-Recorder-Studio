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

class ProfessionalAudioRecorder(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 1
    }

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private var rawFile: File? = null

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
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.enabled = true }
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.enabled = true }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.enabled = true }

        rawFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.pcm")
        running.set(true)
        audioRecord!!.startRecording()
        thread = Thread({ captureLoop(bufferBytes / 2) }, "NexvaryVoiceCapture").apply { start() }
        return rawFile!!
    }

    private fun captureLoop(bufferSamples: Int) {
        val samples = ShortArray(bufferSamples)
        BufferedOutputStream(FileOutputStream(rawFile!!)).use { out ->
            val bb = ByteBuffer.allocate(bufferSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (running.get()) {
                val count = audioRecord?.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING) ?: break
                if (count > 0) {
                    bb.clear()
                    for (i in 0 until count) bb.putShort(samples[i])
                    out.write(bb.array(), 0, count * 2)
                }
            }
        }
    }

    fun stopAndEnhance(destinationWav: File): File {
        if (!running.getAndSet(false)) error("Not recording")
        runCatching { audioRecord?.stop() }
        thread?.join(2500)
        audioRecord?.release()
        audioRecord = null
        thread = null
        val input = rawFile ?: error("No raw recording")
        enhancePcmToWav(input, destinationWav)
        input.delete()
        rawFile = null
        return destinationWav
    }

    private fun enhancePcmToWav(input: File, output: File) {
        val bytes = input.readBytes()
        val count = bytes.size / 2
        val inputShorts = ShortArray(count)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inputShorts)
        val processed = FloatArray(count)

        var prevX = 0f
        var prevY = 0f
        val alpha = 0.995f
        var peak = 0f
        for (i in inputShorts.indices) {
            val x = inputShorts[i] / 32768f
            var y = alpha * (prevY + x - prevX)
            prevX = x
            prevY = y

            val a = abs(y)
            if (a < 0.006f) y *= (a / 0.006f)
            if (abs(y) > 0.42f) {
                val over = abs(y) - 0.42f
                y = sign(y) * (0.42f + over / 3.2f)
            }
            processed[i] = y
            peak = max(peak, abs(y))
        }

        val gain = if (peak > 0.001f) (0.92f / peak).coerceAtMost(4f) else 1f
        val pcm = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        processed.forEach { f ->
            val s = (f * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            pcm.putShort(s)
        }
        writeWav(output, pcm.array(), SAMPLE_RATE, CHANNELS, 16)
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int, channels: Int, bits: Int) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            val byteRate = sampleRate * channels * bits / 8
            val blockAlign = channels * bits / 8
            fun leInt(v: Int) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte())) }
            fun leShort(v: Int) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())) }
            out.writeBytes("RIFF")
            leInt(36 + pcm.size)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            leInt(16)
            leShort(1)
            leShort(channels)
            leInt(sampleRate)
            leInt(byteRate)
            leShort(blockAlign)
            leShort(bits)
            out.writeBytes("data")
            leInt(pcm.size)
            out.write(pcm)
        }
    }
}
