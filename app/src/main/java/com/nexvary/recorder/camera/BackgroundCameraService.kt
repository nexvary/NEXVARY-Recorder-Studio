package com.nexvary.recorder.camera

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import java.io.File

/**
 * User-initiated recording service. Start from a visible Activity, before locking
 * the device or navigating away. Never start camera capture from a background receiver.
 */
class BackgroundCameraService : Service() {
    companion object {
        const val ACTION_START = "com.nexvary.recorder.camera.START"
        const val ACTION_STOP = "com.nexvary.recorder.camera.STOP"
        const val EXTRA_FRONT = "front"
        const val EXTRA_AUDIO = "audio"
        private const val CHANNEL = "nexvary_camera_recording"
        private const val NOTIFICATION_ID = 2406
    }

    private val manager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private var recorder: MediaRecorder? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var output: File? = null
    private var started = false
    private var opening = false
    private val thread = HandlerThread("NexvaryCameraRecording").apply { start() }
    private val handler = Handler(thread.looper)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || started || opening) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        val audio = intent.getBooleanExtra(EXTRA_AUDIO, false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    (if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                startForeground(NOTIFICATION_ID, notification(), type)
            } else startForeground(NOTIFICATION_ID, notification())
            openCamera(intent.getBooleanExtra(EXTRA_FRONT, false), audio)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Camera recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, BackgroundCameraService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_feature_screen)
            .setContentTitle("NEXVARY Recorder Studio")
            .setContentText("Camera recording active — tap Stop to save")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop & save", pending)
            .build()
    }

    private fun openCamera(front: Boolean, audio: Boolean) {
        opening = true
        try {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            } ?: error("Requested camera unavailable")
            val characteristics = manager.getCameraCharacteristics(id)
            val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val supported = config?.getOutputSizes(MediaRecorder::class.java).orEmpty()
            val size = supported.filter { it.width <= 1920 && it.height <= 1080 }
                .maxByOrNull { it.width.toLong() * it.height } ?: supported.firstOrNull()
                ?: error("No supported recording resolution")
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
                "NEXVARY_${System.currentTimeMillis()}.mp4")
            output = file
            val media = MediaRecorder()
            recorder = media
            if (audio) media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audio) media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setVideoSize(size.width, size.height)
            media.setVideoFrameRate(30)
            media.setVideoEncodingBitRate(5_000_000)
            if (audio) media.setAudioEncodingBitRate(128_000)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                stopSelf(); return
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    try {
                        val surface = media.surface
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(capture: CameraCaptureSession) {
                                session = capture
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                                        .apply { addTarget(surface) }.build()
                                    capture.setRepeatingRequest(request, null, handler)
                                    media.start()
                                    started = true
                                    opening = false
                                } catch (_: Exception) { stopSelf() }
                            }
                            override fun onConfigureFailed(capture: CameraCaptureSession) { stopSelf() }
                        }, handler)
                    } catch (_: Exception) { stopSelf() }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); stopSelf() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); stopSelf() }
            }, handler)
        } catch (_: Exception) { stopSelf() }
    }

    override fun onDestroy() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        if (started) runCatching { recorder?.stop() }.onFailure { output?.delete() }
        else output?.delete()
        runCatching { recorder?.reset(); recorder?.release() }
        session = null; device = null; recorder = null
        thread.quitSafely()
        super.onDestroy()
    }
}
