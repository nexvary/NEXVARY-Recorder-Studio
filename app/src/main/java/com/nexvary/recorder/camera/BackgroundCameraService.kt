package com.nexvary.recorder.camera

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.*
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.*
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
import java.io.File

/** Starts only from a visible activity. All camera/recorder ownership is serialized on one looper. */
class BackgroundCameraService : Service() {
    companion object {
        const val ACTION_START = "com.nexvary.recorder.camera.START"
        const val ACTION_STOP = "com.nexvary.recorder.camera.STOP"
        const val EXTRA_FRONT = "front"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_DURATION = "duration_ms"
        const val EXTRA_RECIPIENT = "email_recipient"
        @Volatile var activeOutputPath: String? = null
            private set
        enum class Stage { IDLE, STARTING, RECORDING, SAVING, SAVED, ERROR }
        data class CaptureState(val stage: Stage = Stage.IDLE, val deadline: Long = 0, val name: String = "", val mailQueued: Boolean = false, val mailRequested: Boolean = false)
        @Volatile var state = CaptureState()
            private set
        private const val CHANNEL = "nexvary_camera_recording"
        private const val NOTIFICATION_ID = 2406
    }
    private val manager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private val thread = HandlerThread("NexvaryCameraRecording").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var output: File? = null
    @Volatile private var finalState: CaptureState? = null
    private var started = false
    private var closing = false
    private var openPending = false
    private var destroyed = false
    private var requested = false
    private var recipient = ""
    private var duration = 0L

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { handler.post { finishRecording() }; return START_NOT_STICKY }
        if (intent?.action != ACTION_START || requested) return START_NOT_STICKY
        duration = intent.getLongExtra(EXTRA_DURATION, 0)
        recipient = intent.getStringExtra(EXTRA_RECIPIENT).orEmpty().trim()
        if (!DeliveryPolicy.validDuration(duration) || (recipient.isNotBlank() && !DeliveryPolicy.email(recipient)) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            state = CaptureState(Stage.ERROR); stopSelf(); return START_NOT_STICKY
        }
        requested = true; state = CaptureState(Stage.STARTING)
        val audio = intent.getBooleanExtra(EXTRA_AUDIO, false) && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val front = intent.getBooleanExtra(EXTRA_FRONT, false)
        val channel = NotificationChannel(CHANNEL, getString(R.string.camera_title), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                (if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0))
            else startForeground(NOTIFICATION_ID, notification())
            handler.post { openCamera(front, audio) }
        } catch (_: Exception) { state = CaptureState(Stage.ERROR); stopSelf() }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val pending = PendingIntent.getService(this, 0, Intent(this, BackgroundCameraService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, BackgroundCameraActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_feature_screen)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(if (duration > 0) R.string.camera_timed_notification else R.string.camera_recording))
            .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, getString(R.string.camera_stop), pending).build()
    }
    private fun openCamera(front: Boolean, audio: Boolean) {
        if (closing) return
        try {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
                ?: error("Camera unavailable")
            val characteristics = manager.getCameraCharacteristics(id)
            val supported = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java).orEmpty()
            val size = supported.filter { it.width <= 1920 && it.height <= 1080 }.maxByOrNull { it.width.toLong() * it.height }
                ?: supported.firstOrNull() ?: error("Recording unsupported")
            val file = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NEXVARY_${System.currentTimeMillis()}.mp4")
            output = file; activeOutputPath = file.absolutePath
            val media = MediaRecorder(); recorder = media
            if (audio) media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audio) media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setVideoSize(size.width, size.height); media.setVideoFrameRate(30); media.setVideoEncodingBitRate(5_000_000)
            if (audio) media.setAudioEncodingBitRate(128_000)
            media.setOrientationHint(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0)
            media.setOutputFile(file.absolutePath)
            // Enforced by Android's recorder, including while the activity is stopped / screen is off.
            if (duration > 0) media.setMaxDuration(duration.toInt())
            media.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) handler.post { finishRecording() }
            }
            media.setOnErrorListener { _, _, _ -> handler.post { finishRecording() } }
            media.prepare()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { finishRecording(); return }
            openPending = true
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    openPending = false
                    if (closing) { camera.close(); if (destroyed) thread.quitSafely(); return }
                    device = camera
                    try {
                        val surface = media.surface
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(capture: CameraCaptureSession) {
                                if (closing) { capture.close(); return }
                                session = capture
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(surface) }.build()
                                    capture.setRepeatingRequest(request, null, handler); media.start(); started = true
                                    state = CaptureState(Stage.RECORDING, if (duration > 0) SystemClock.elapsedRealtime() + duration else 0)
                                } catch (_: Exception) { finishRecording() }
                            }
                            override fun onConfigureFailed(capture: CameraCaptureSession) { capture.close(); finishRecording() }
                        }, handler)
                    } catch (_: Exception) { finishRecording() }
                }
                override fun onDisconnected(camera: CameraDevice) { openPending = false; camera.close(); finishRecording(); if (destroyed) thread.quitSafely() }
                override fun onError(camera: CameraDevice, error: Int) { openPending = false; camera.close(); finishRecording(); if (destroyed) thread.quitSafely() }
            }, handler)
        } catch (_: Exception) { finishRecording() }
    }
    private fun finishRecording() {
        if (closing) return
        closing = true
        state = state.copy(stage = Stage.SAVING)
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }; runCatching { device?.close() }
        if (started) runCatching { recorder?.stop() }
        runCatching { recorder?.reset(); recorder?.release() }
        session = null; device = null; recorder = null
        val file = output
        val valid = started && file != null && file.isFile && file.length() > 0 && runCatching {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.absolutePath)
                (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0 &&
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            } finally { metadata.release() }
        }.getOrDefault(false)
        var queued = false
        if (valid && file != null && recipient.isNotBlank()) queued = runCatching {
            val profile = SmtpSettings(this).load()
            val job = MailQueue(this).enqueue(file, recipient, profile?.limitMiB ?: 25)
            if (job.state == MailQueue.State.QUEUED) MailWorker.schedule(this, job.id)
            true
        }.getOrDefault(false)
        if (!valid) file?.delete()
        val finished = if (valid && file != null) CaptureState(Stage.SAVED, name = file.name, mailQueued = queued, mailRequested = recipient.isNotBlank()) else CaptureState(Stage.ERROR)
        finalState = finished
        activeOutputPath = null
        if (destroyed) state = finished
        main.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    override fun onDestroy() {
        finalState?.let { state = it }
        handler.post { destroyed = true; finishRecording(); if (openPending) handler.postDelayed({ thread.quitSafely() }, 10_000) else thread.quitSafely() }
        super.onDestroy()
    }
}
