package com.nexvary.recorder.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object MediaMuxerUtil {
    fun replaceAudio(context: Context, videoUri: Uri, audioUri: Uri, outputFd: java.io.FileDescriptor, cacheDir: File) {
        val audioMime = context.contentResolver.getType(audioUri).orEmpty().lowercase()
        val actualAudioUri: Uri
        var tempAudio: File? = null
        if (audioMime.contains("wav") || audioUri.toString().lowercase().endsWith(".wav")) {
            val tempWav = File(cacheDir, "source_${System.currentTimeMillis()}.wav")
            context.contentResolver.openInputStream(audioUri)!!.use { input -> tempWav.outputStream().use { input.copyTo(it) } }
            tempAudio = File(cacheDir, "encoded_${System.currentTimeMillis()}.m4a")
            WavAacEncoder.encode(tempWav, tempAudio)
            tempWav.delete()
            actualAudioUri = Uri.fromFile(tempAudio)
        } else actualAudioUri = audioUri

        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        try {
            context.contentResolver.openAssetFileDescriptor(videoUri, "r")!!.use { afd ->
                videoEx.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            if (actualAudioUri.scheme == "file") audioEx.setDataSource(actualAudioUri.path!!)
            else context.contentResolver.openAssetFileDescriptor(actualAudioUri, "r")!!.use { afd ->
                audioEx.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }

            val videoTrack = findTrack(videoEx, "video/")
            val audioTrack = findTrack(audioEx, "audio/")
            require(videoTrack >= 0) { "الفيديو لا يحتوي مسار فيديو صالح" }
            require(audioTrack >= 0) { "ملف الصوت غير مدعوم" }
            videoEx.selectTrack(videoTrack)
            audioEx.selectTrack(audioTrack)
            val videoFormat = videoEx.getTrackFormat(videoTrack)
            val audioFormat = audioEx.getTrackFormat(audioTrack)
            val videoDurationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) videoFormat.getLong(MediaFormat.KEY_DURATION) else Long.MAX_VALUE

            val muxer = MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val retriever = MediaMetadataRetriever()
            context.contentResolver.openAssetFileDescriptor(videoUri, "r")!!.use { afd ->
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let { muxer.setOrientationHint(it) }
            retriever.release()

            val outVideo = muxer.addTrack(videoFormat)
            val outAudio = muxer.addTrack(audioFormat)
            muxer.start()
            copyTrack(videoEx, muxer, outVideo, Long.MAX_VALUE)
            copyTrack(audioEx, muxer, outAudio, videoDurationUs)
            muxer.stop()
            muxer.release()
        } finally {
            videoEx.release()
            audioEx.release()
            tempAudio?.delete()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun copyTrack(extractor: MediaExtractor, muxer: MediaMuxer, outTrack: Int, maxPtsUs: Long) {
        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val pts = extractor.sampleTime
            if (pts < 0 || pts > maxPtsUs) break
            info.set(0, size, pts, extractor.sampleFlags)
            muxer.writeSampleData(outTrack, buffer, info)
            extractor.advance()
        }
    }
}
