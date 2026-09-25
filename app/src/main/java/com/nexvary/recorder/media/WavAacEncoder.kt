package com.nexvary.recorder.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavAacEncoder {
    data class WavInfo(val sampleRate: Int, val channels: Int, val bits: Int, val dataOffset: Long, val dataSize: Long)

    fun encode(wav: File, outM4a: File, bitrate: Int = 192_000) {
        val info = parseWav(wav)
        require(info.bits == 16) { "Only PCM16 WAV is supported" }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, info.sampleRate, info.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(outM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameBytes = info.channels * 2
        var totalFrames = 0L
        var inputDone = false
        var outputDone = false

        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(info.dataOffset)
            var remaining = info.dataSize
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index) ?: continue
                        input.clear()
                        val maxRead = minOf(input.remaining().toLong(), remaining).toInt()
                        val pts = totalFrames * 1_000_000L / info.sampleRate
                        if (maxRead > 0) {
                            val bytes = ByteArray(maxRead)
                            val read = raf.read(bytes)
                            if (read > 0) {
                                input.put(bytes, 0, read)
                                codec.queueInputBuffer(index, 0, read, pts, 0)
                                remaining -= read
                                totalFrames += read / frameBytes
                            }
                        } else {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                while (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)
                    if (bufferInfo.size > 0 && encoded != null && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(track, encoded, bufferInfo)
                    }
                    outputDone = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (outputDone) break
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }
        }
        runCatching { codec.stop() }
        codec.release()
        if (muxerStarted) runCatching { muxer.stop() }
        muxer.release()
    }

    private fun parseWav(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            fun read4(): String { val b = ByteArray(4); raf.readFully(b); return String(b, Charsets.US_ASCII) }
            fun leInt(): Int { val b = ByteArray(4); raf.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int }
            fun leShort(): Int { val b = ByteArray(2); raf.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff }
            require(read4() == "RIFF") { "Not RIFF WAV" }
            leInt()
            require(read4() == "WAVE") { "Not WAVE" }
            var sampleRate = 0
            var channels = 0
            var bits = 0
            var dataOffset = -1L
            var dataSize = 0L
            while (raf.filePointer + 8 <= raf.length()) {
                val id = read4()
                val size = leInt()
                val start = raf.filePointer
                when (id) {
                    "fmt " -> {
                        val audioFormat = leShort()
                        require(audioFormat == 1) { "WAV must be PCM" }
                        channels = leShort()
                        sampleRate = leInt()
                        raf.skipBytes(6)
                        bits = leShort()
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size.toLong()
                        break
                    }
                }
                raf.seek(start + size + (size and 1))
            }
            require(dataOffset >= 0 && sampleRate > 0 && channels in 1..2) { "Unsupported WAV" }
            return WavInfo(sampleRate, channels, bits, dataOffset, dataSize)
        }
    }
}
