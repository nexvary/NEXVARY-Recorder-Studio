package com.ibmempire.recorder.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object VideoToolsEngine {
    enum class Container(
        val extension: String,
        val mime: String,
        val muxerFormat: Int
    ) {
        MP4(
            "mp4",
            "video/mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        ),
        THREE_GPP(
            "3gp",
            "video/3gpp",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
        ),
        WEBM(
            "webm",
            "video/webm",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        )
    }

    data class TrimResult(
        val requestedStartMs: Long,
        val actualStartMs: Long,
        val endMs: Long
    )

    fun trim(
        context: Context,
        input: Uri,
        output: File,
        startMs: Long,
        endMs: Long
    ): TrimResult {
        require(startMs >= 0L) { "Start time must be zero or greater" }
        require(endMs > startMs) { "End time must be greater than start time" }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            setDataSource(context, extractor, input)
            val tracks = mediaTracks(extractor)
            require(tracks.isNotEmpty()) { "No audio/video tracks found" }

            muxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            applyRotation(context, input, muxer)

            val trackMap = mutableMapOf<Int, Int>()
            for (track in tracks) {
                val format = extractor.getTrackFormat(track)
                val outTrack = muxer.addTrack(format)
                trackMap[track] = outTrack
                extractor.selectTrack(track)
            }

            muxer.start()
            muxerStarted = true

            val requestedStartUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            extractor.seekTo(
                requestedStartUs,
                MediaExtractor.SEEK_TO_PREVIOUS_SYNC
            )

            val firstSampleUs = extractor.sampleTime.coerceAtLeast(0L)
            val buffer = ByteBuffer.allocateDirect(maxInputSize(extractor, tracks))
            val info = MediaCodec.BufferInfo()

            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > endUs) break

                val outTrack = trackMap[track]
                if (outTrack == null) {
                    extractor.advance()
                    continue
                }

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                info.set(
                    0,
                    size,
                    (sampleTimeUs - firstSampleUs).coerceAtLeast(0L),
                    codecFlags(extractor.sampleFlags)
                )
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }

            return TrimResult(
                requestedStartMs = startMs,
                actualStartMs = firstSampleUs / 1_000L,
                endMs = endMs
            )
        } finally {
            extractor.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    fun convertContainer(
        context: Context,
        input: Uri,
        output: File,
        target: Container
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            setDataSource(context, extractor, input)
            val tracks = mediaTracks(extractor)
            require(tracks.any {
                extractor.getTrackFormat(it)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                    .startsWith("video/")
            }) { "No video track found" }

            validateTargetCompatibility(extractor, tracks, target)

            muxer = MediaMuxer(output.absolutePath, target.muxerFormat)
            if (target != Container.WEBM) {
                applyRotation(context, input, muxer)
            }

            val trackMap = mutableMapOf<Int, Int>()
            for (track in tracks) {
                val format = extractor.getTrackFormat(track)
                val outTrack = muxer.addTrack(format)
                trackMap[track] = outTrack
                extractor.selectTrack(track)
            }

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(maxInputSize(extractor, tracks))
            val info = MediaCodec.BufferInfo()
            var basePtsUs = -1L

            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break

                val outTrack = trackMap[track]
                if (outTrack == null) {
                    extractor.advance()
                    continue
                }

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                val pts = extractor.sampleTime.coerceAtLeast(0L)
                if (basePtsUs < 0L) basePtsUs = pts

                info.set(
                    0,
                    size,
                    (pts - basePtsUs).coerceAtLeast(0L),
                    codecFlags(extractor.sampleFlags)
                )
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException(
                "The selected video codec is not compatible with ${target.extension.uppercase()}. " +
                    "Choose another output format.",
                error
            )
        } finally {
            extractor.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun validateTargetCompatibility(
        extractor: MediaExtractor,
        tracks: List<Int>,
        target: Container
    ) {
        if (target != Container.WEBM) return

        val allowedVideo = setOf(
            "video/x-vnd.on2.vp8",
            "video/x-vnd.on2.vp9",
            "video/av01"
        )
        val allowedAudio = setOf(
            "audio/vorbis",
            "audio/opus"
        )

        for (track in tracks) {
            val mime = extractor.getTrackFormat(track)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()

            if (mime.startsWith("video/") && mime !in allowedVideo) {
                error("WebM requires VP8, VP9 or AV1 video on this device")
            }
            if (mime.startsWith("audio/") && mime !in allowedAudio) {
                error("WebM requires Vorbis or Opus audio on this device")
            }
        }
    }

    private fun mediaTracks(extractor: MediaExtractor): List<Int> =
        (0 until extractor.trackCount).filter { index ->
            val mime = extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
            mime.startsWith("video/") || mime.startsWith("audio/")
        }

    private fun maxInputSize(
        extractor: MediaExtractor,
        tracks: List<Int>
    ): Int {
        val reported = tracks.maxOfOrNull { index ->
            val format = extractor.getTrackFormat(index)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                0
            }
        } ?: 0

        return reported
            .coerceAtLeast(1 * 1024 * 1024)
            .coerceAtMost(16 * 1024 * 1024)
    }

    private fun codecFlags(extractorFlags: Int): Int {
        var flags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (
            android.os.Build.VERSION.SDK_INT >= 26 &&
            (extractorFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0
        ) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun setDataSource(
        context: Context,
        extractor: MediaExtractor,
        uri: Uri
    ) {
        context.contentResolver.openAssetFileDescriptor(uri, "r")!!.use { afd ->
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
    }

    private fun applyRotation(
        context: Context,
        uri: Uri,
        muxer: MediaMuxer
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")!!.use { afd ->
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

            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { rotation ->
                    if (rotation in setOf(0, 90, 180, 270)) {
                        muxer.setOrientationHint(rotation)
                    }
                }
        } finally {
            retriever.release()
        }
    }
}
