package com.nexvary.recorder.audio

import java.io.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** Two streaming passes normalize PCM without holding a long recording in memory. */
object PcmEnhancer {
    private const val BLOCK_BYTES = 16 * 1024
    private class Filter {
        private var previousX = 0f
        private var previousY = 0f
        fun apply(sample: Short): Float {
            val x = sample / 32768f
            var y = 0.995f * (previousY + x - previousX)
            previousX = x; previousY = y
            val amplitude = abs(y)
            if (amplitude < 0.006f) y *= amplitude / 0.006f
            if (abs(y) > 0.42f) y = sign(y) * (0.42f + (abs(y) - 0.42f) / 3.2f)
            return y
        }
    }
    private fun samples(input: File, consume: (Float) -> Unit) {
        val filter = Filter()
        DataInputStream(BufferedInputStream(input.inputStream())).use { stream ->
            val block = ByteArray(BLOCK_BYTES)
            var remaining = input.length()
            while (remaining > 0) {
                val count = minOf(block.size.toLong(), remaining).toInt()
                stream.readFully(block, 0, count)
                for (i in 0 until count step 2) consume(filter.apply(((block[i].toInt() and 0xff) or (block[i + 1].toInt() shl 8)).toShort()))
                remaining -= count
            }
        }
    }
    fun enhance(input: File, output: File, sampleRate: Int = 48_000) {
        val length = input.length()
        require(input.canonicalFile != output.canonicalFile && length > 0 && length % 2 == 0L && length <= 0xffff_ffffL - 36)
        require(sampleRate in 8_000..192_000)
        var peak = 0f
        samples(input) { peak = max(peak, abs(it)) }
        val gain = if (peak > 0.001f) (0.92f / peak).coerceAtMost(4f) else 1f
        try {
            DataOutputStream(BufferedOutputStream(output.outputStream())).use { stream ->
                fun intLE(value: Long) { repeat(4) { stream.writeByte((value shr (it * 8)).toInt()) } }
                fun shortLE(value: Int) { stream.writeByte(value); stream.writeByte(value shr 8) }
                stream.writeBytes("RIFF"); intLE(36 + length); stream.writeBytes("WAVEfmt "); intLE(16)
                shortLE(1); shortLE(1); intLE(sampleRate.toLong()); intLE(sampleRate * 2L); shortLE(2); shortLE(16)
                stream.writeBytes("data"); intLE(length)
                samples(input) { sample -> shortLE((sample * gain * 32767f).toInt().coerceIn(-32768, 32767)) }
            }
        } catch (error: Exception) { output.delete(); throw error }
    }
}
