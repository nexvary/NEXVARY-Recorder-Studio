package com.ibmempire.recorder.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFormatConverter {
    enum class Target(val extension: String, val mime: String) {
        WAV("wav", "audio/wav"),
        M4A("m4a", "audio/mp4"),
        OGG("ogg", "audio/ogg")
    }

    private data class PcmInfo(
        val sampleRate: Int,
        val channels: Int,
        val bytesPerSample: Int = 2
    )

    fun convert(
        context: Context,
        input: Uri,
        target: Target,
        output: File,
        workDir: File
    ) {
        val pcm = File(workDir, "audio_${System.nanoTime()}.pcm")
        try {
            val info = decodeToPcm16(context, input, pcm)
            when (target) {
                Target.WAV -> writeWav(pcm, output, info)
                Target.M4A -> encodePcm(
                    pcm = pcm,
                    output = output,
                    info = info,
                    mime = MediaFormat.MIMETYPE_AUDIO_AAC,
                    bitrate = 192_000,
                    muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )
                Target.OGG -> encodePcm(
                    pcm = pcm,
                    output = output,
                    info = info,
                    mime = MediaFormat.MIMETYPE_AUDIO_OPUS,
                    bitrate = 128_000,
                    muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                )
            }
        } finally {
            pcm.delete()
        }
    }

    private fun decodeToPcm16(
        context: Context,
        input: Uri,
        output: File
    ): PcmInfo {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            context.contentResolver.openAssetFileDescriptor(input, "r")!!.use { afd ->
                if (afd.length >= 0) {
                    extractor.setDataSource(
                        afd.fileDescriptor,
                        afd.startOffset,
                        afd.length
                    )
                } else {
                    extractor.setDataSource(afd.fileDescriptor)
                }
            }

            val track = findTrack(extractor, "audio/")
            require(track >= 0) { "No supported audio track found" }

            extractor.selectTrack(track)
            val sourceFormat = extractor.getTrackFormat(track)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Audio MIME type is missing")

            runCatching {
                sourceFormat.setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()

            var sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            FileOutputStream(output).use { out ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = decoder.getInputBuffer(inIndex)
                                ?: error("Decoder input buffer unavailable")
                            buffer.clear()

                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    size,
                                    extractor.sampleTime.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val pcmEncoding = if (
                                format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ) {
                                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            require(
                                pcmEncoding == AudioFormat.ENCODING_PCM_16BIT
                            ) { "Decoder did not provide PCM16 output" }
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                        else -> if (outIndex >= 0) {
                            val buffer = decoder.getOutputBuffer(outIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                out.write(bytes)
                            }

                            outputDone =
                                (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            decoder.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }
            }

            require(sampleRate > 0 && channels in 1..2) {
                "Unsupported decoded audio layout"
            }
            return PcmInfo(sampleRate, channels)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    private fun encodePcm(
        pcm: File,
        output: File,
        info: PcmInfo,
        mime: String,
        bitrate: Int,
        muxerFormat: Int
    ) {
        val format = MediaFormat.createAudioFormat(
            mime,
            info.sampleRate,
            info.channels
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
            }
        }

        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(format)
            ?: error("This device has no encoder for $mime")

        val codec = MediaCodec.createByCodecName(codecName)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var track = -1

        try {
            codec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            codec.start()

            muxer = MediaMuxer(output.absolutePath, muxerFormat)
            val bufferInfo = MediaCodec.BufferInfo()
            val frameBytes = info.channels * info.bytesPerSample
            var totalFrames = 0L
            var inputDone = false
            var outputDone = false

            FileInputStream(pcm).use { input ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex)
                                ?: error("Encoder input buffer unavailable")
                            buffer.clear()

                            val bytes = ByteArray(buffer.remaining())
                            val read = input.read(bytes)
                            val ptsUs =
                                totalFrames * 1_000_000L / info.sampleRate

                            if (read < 0) {
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    ptsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                buffer.put(bytes, 0, read)
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    read,
                                    ptsUs,
                                    0
                                )
                                totalFrames += read / frameBytes
                            }
                        }
                    }

                    when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                        else -> if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            val isCodecConfig =
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                            if (
                                buffer != null &&
                                bufferInfo.size > 0 &&
                                muxerStarted &&
                                !isCodecConfig
                            ) {
                                buffer.position(bufferInfo.offset)
                                buffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(track, buffer, bufferInfo)
                            }

                            outputDone =
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun writeWav(
        pcm: File,
        output: File,
        info: PcmInfo
    ) {
        val dataSize = pcm.length()
        val byteRate =
            info.sampleRate * info.channels * info.bytesPerSample
        val blockAlign = info.channels * info.bytesPerSample

        FileOutputStream(output).use { out ->
            fun ascii(value: String) =
                out.write(value.toByteArray(Charsets.US_ASCII))

            fun leInt(value: Int) {
                out.write(
                    ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(value)
                        .array()
                )
            }

            fun leShort(value: Int) {
                out.write(
                    ByteBuffer.allocate(2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(value.toShort())
                        .array()
                )
            }

            ascii("RIFF")
            leInt((36L + dataSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ascii("WAVE")
            ascii("fmt ")
            leInt(16)
            leShort(1)
            leShort(info.channels)
            leInt(info.sampleRate)
            leInt(byteRate)
            leShort(blockAlign)
            leShort(16)
            ascii("data")
            leInt(dataSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

            FileInputStream(pcm).use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun findTrack(
        extractor: MediaExtractor,
        prefix: String
    ): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}
