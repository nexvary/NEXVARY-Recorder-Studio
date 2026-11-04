package com.nexvary.recorder.camera

import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import java.io.File
import java.util.PriorityQueue

/** One camera surface and live encoders for the entire session. Only MP4 muxers rotate.
 * All methods run on the camera looper. SMTP, hashing and validation run elsewhere.
 * Audio/video are ordered by a cross-track watermark before selecting a keyframe boundary.
 */
class SegmentedEncoder(
    private val context: android.content.Context, private val handler: Handler, private val quality: CameraQuality,
    private val size: CameraQuality.FrameSize, private val fps: Int, private val audio: Boolean,
    seconds: Int, private val orientation: Int,
    private val newFile: (Int, Long) -> File,
    private val finalized: (File, Int, Long, Long) -> Unit,
    private val failure: (String) -> Unit
) {
    private val timeline = SegmentTimeline(seconds)
    private var video: MediaCodec? = null
    private var sound: MediaCodec? = null
    private var microphone: AudioRecord? = null
    private var inputSurface: Surface? = null
    private val formats = arrayOfNulls<MediaFormat>(2)
    private val origins = longArrayOf(0, 0)
    private val originReady = booleanArrayOf(false, false)
    private val latest = longArrayOf(-1, -1)
    private val lastWritten = longArrayOf(-1, -1)
    private data class Sample(val track: Int, val pts: Long, val flags: Int, val bytes: ByteArray)
    private val samples = PriorityQueue<Sample>(compareBy<Sample> { it.pts }.thenBy { it.track })
    private var pendingBytes = 0
    private var muxer: MediaMuxer? = null
    private var tracks = intArrayOf(-1, -1)
    private var file: File? = null
    private var lastVideoUs = -1L
    private var live = false
    private var stopping = false
    private var videoEos = false
    private var audioEos = !audio
    private var audioFrames = 0L
    private var audioInputEos = false
    private var lastSyncRequest = -1L
    private var lastProgress = SystemClock.elapsedRealtime()
    private var maxSeenUs = 0L
    private val pump = object : Runnable {
        override fun run() {
            if (!live) return
            try {
                feedAudio(false); drain(0); if (audio) drain(1); writeReady(false)
                check(SystemClock.elapsedRealtime() - lastProgress < 5_000) { "encoder_stalled" }
                handler.postDelayed(this, 10)
            } catch (_: Exception) { live = false; failure("encoder_or_storage") }
        }
    }
    fun prepare(): Surface {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC); video = codec
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface(); inputSurface = surface
        if (audio) {
            check(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            val rate = quality.audioSampleRate
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0)
            microphone = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 8192)).also { check(it.state == AudioRecord.STATE_INITIALIZED) }
            sound = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also { encoder ->
                encoder.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, quality.audioBitrate)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
                }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        }
        codec.start(); sound?.start()
        return surface
    }
    fun start() {
        microphone?.startRecording()
        check(microphone == null || microphone?.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        live = true; lastProgress = SystemClock.elapsedRealtime(); handler.post(pump)
    }
    private fun feedAudio(eos: Boolean) {
        val codec = sound ?: return
        if (audioInputEos) return
        val index = codec.dequeueInputBuffer(0)
        if (index < 0) return
        val buffer = requireNotNull(codec.getInputBuffer(index)); buffer.clear()
        val count = if (eos) 0 else requireNotNull(microphone).read(buffer, minOf(buffer.capacity(), 4096), AudioRecord.READ_NON_BLOCKING)
        check(count >= 0) { "microphone_read" }
        val pts = audioFrames * 1_000_000L / quality.audioSampleRate
        codec.queueInputBuffer(index, 0, count, pts, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
        audioFrames += count / 2
        if (eos) audioInputEos = true
    }
    private fun drain(track: Int) {
        val codec = (if (track == 0) video else sound) ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { formats[track] = codec.outputFormat; continue }
            if (index < 0) break
            try {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    if (!originReady[track]) { origins[track] = info.presentationTimeUs; originReady[track] = true }
                    val pts = info.presentationTimeUs - origins[track]
                    check(pts >= latest[track]) { "non_monotonic_encoder" }
                    latest[track] = pts
                    maxSeenUs = maxOf(maxSeenUs, pts)
                    if (track == 0 && timeline.requestSync(pts) && pts - lastSyncRequest >= 1_000_000) {
                        codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                        lastSyncRequest = pts
                    }
                    val buffer = requireNotNull(codec.getOutputBuffer(index))
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size); buffer.get(bytes)
                    samples.add(Sample(track, pts, info.flags, bytes)); pendingBytes += bytes.size
                    check(pendingBytes <= 16 * 1024 * 1024) { "encoder_queue_full" }
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (track == 0) videoEos = true else audioEos = true
                }
            } finally { codec.releaseOutputBuffer(index, false) }
        }
    }
    private fun writeReady(all: Boolean) {
        if (formats[0] == null || (audio && formats[1] == null)) return
        val watermark = if (all) Long.MAX_VALUE else if (audio) minOf(latest[0], latest[1]) else latest[0]
        while (samples.isNotEmpty() && samples.peek().pts <= watermark) {
            val sample = samples.remove(); pendingBytes -= sample.bytes.size
            if (sample.track == 0 && timeline.acceptVideo(sample.pts, sample.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)) {
                // timeline has advanced; close the previous muxer using its own retained origin.
                closeMuxer()
                openMuxer(sample.pts)
            }
            val target = muxer ?: continue
            if (sample.pts < timeline.startUs) continue
            val relative = sample.pts - timeline.startUs
            check(relative > lastWritten[sample.track]) { "duplicate_timestamp" }
            val info = MediaCodec.BufferInfo().apply { set(0, sample.bytes.size, relative, sample.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()) }
            target.writeSampleData(tracks[sample.track], java.nio.ByteBuffer.wrap(sample.bytes), info)
            lastWritten[sample.track] = relative
            if (sample.track == 0) lastVideoUs = sample.pts
            lastProgress = SystemClock.elapsedRealtime()
        }
        if (samples.isNotEmpty()) check(maxSeenUs - samples.peek().pts < 5_000_000) { "track_stalled" }
    }
    private var muxerIndex = 0
    private var muxerStart = 0L
    private fun openMuxer(pts: Long) {
        muxerIndex = timeline.index; muxerStart = pts
        val next = newFile(muxerIndex, pts); file = next
        val mux = MediaMuxer(next.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); muxer = mux
        mux.setOrientationHint(orientation)
        tracks = intArrayOf(mux.addTrack(requireNotNull(formats[0])), if (audio) mux.addTrack(requireNotNull(formats[1])) else -1)
        lastWritten.fill(-1); mux.start()
    }
    private fun closeMuxer() {
        val mux = muxer ?: return
        muxer = null
        try { mux.stop() } finally { mux.release() }
        val saved = requireNotNull(file); file = null
        finalized(saved, muxerIndex, muxerStart, lastVideoUs)
    }
    /** Camera producer must already be closed. Drain EOS with a bounded deadline. */
    fun stop() {
        if (stopping) return
        stopping = true; live = false; handler.removeCallbacks(pump)
        try {
            microphone?.stop()
            video?.signalEndOfInputStream()
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while ((!videoEos || !audioEos) && SystemClock.elapsedRealtime() < deadline) {
                feedAudio(true); drain(0); if (audio) drain(1); writeReady(false)
                Thread.sleep(5)
            }
            writeReady(true); closeMuxer()
            check(videoEos && audioEos) { "encoder_eos_timeout" }
        } finally { release() }
    }
    fun release() {
        live = false; handler.removeCallbacks(pump)
        runCatching { muxer?.release() }; muxer = null
        runCatching { microphone?.release() }; microphone = null
        for (codec in listOf(video, sound)) { runCatching { codec?.stop() }; runCatching { codec?.release() } }
        video = null; sound = null
        runCatching { inputSurface?.release() }; inputSurface = null
        samples.clear()
    }
}
