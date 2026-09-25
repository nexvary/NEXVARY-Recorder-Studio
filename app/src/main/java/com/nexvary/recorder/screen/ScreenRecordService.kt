package com.nexvary.recorder.screen

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexvary.recorder.MainActivity
import com.nexvary.recorder.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenRecordService : Service() {
    companion object {
        const val ACTION_START = "com.nexvary.recorder.START_RECORD"
        const val ACTION_STOP = "com.nexvary.recorder.STOP_RECORD"
        const val ACTION_STATUS = "com.nexvary.recorder.RECORD_STATUS"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_MIC = "record_mic"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"

        const val STATE_STARTING = "starting"
        const val STATE_STARTED = "started"
        const val STATE_STOPPED = "stopped"
        const val STATE_ERROR = "error"

        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 1201

        @Volatile var isRecording: Boolean = false
            private set
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: Uri? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null
    private var recorderStarted = false
    private var stopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finishRecording(fromProjectionCallback = true, userInitiated = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> finishRecording(fromProjectionCallback = false, userInitiated = true)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (isRecording || recorder != null) {
            sendStatus(STATE_ERROR, getString(R.string.screen_already_running))
            return
        }

        val micRequested = intent.getBooleanExtra(EXTRA_MIC, true)
        val useMic = micRequested &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: run {
            sendStatus(STATE_ERROR, getString(R.string.screen_permission_data_missing))
            stopSelf()
            return
        }

        try {
            val foregroundType = if (useMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification(getString(R.string.notification_recording_preparing)), foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification(getString(R.string.notification_recording_preparing)))
            }
            sendStatus(STATE_STARTING, getString(R.string.screen_preparing))

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val (width, height) = chooseCaptureSize(metrics.widthPixels, metrics.heightPixels)
            val bitrate = (width.toLong() * height.toLong() * 5L)
                .coerceIn(4_000_000L, 12_000_000L)
                .toInt()

            createOutput()
            val fd = outputPfd?.fileDescriptor ?: error(getString(R.string.screen_file_create_fail))

            recorder = createRecorder().apply {
                if (useMic) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                if (useMic) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(192_000)
                    setAudioSamplingRate(48_000)
                }
                setOutputFile(fd)
                prepare()
            }

            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: error(getString(R.string.screen_projection_create_fail))

            projection!!.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            virtualDisplay = projection!!.createVirtualDisplay(
                "NEXVARYRecorder",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface,
                null,
                Handler(Looper.getMainLooper())
            ) ?: error(getString(R.string.screen_virtual_display_fail))

            recorder!!.start()
            recorderStarted = true
            isRecording = true

            val sizeLabel = "${width}×${height}"
            updateNotification(getString(R.string.notification_recording_size_format, sizeLabel))
            sendStatus(STATE_STARTED, getString(R.string.screen_started_size_format, sizeLabel))
        } catch (e: Exception) {
            abortRecording(getString(R.string.recording_failed))
        }
    }

    private fun createOutput() {
        val name = "screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/NEXVARY Recorder"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error(getString(R.string.screen_file_create_fail))
        outputPfd = contentResolver.openFileDescriptor(outputUri!!, "w")
            ?: error(getString(R.string.screen_file_open_fail))
    }

    private fun finishRecording(fromProjectionCallback: Boolean, userInitiated: Boolean) {
        if (stopping) return
        stopping = true

        val hadStarted = recorderStarted
        try {
            if (recorderStarted) {
                runCatching { recorder?.stop() }
            }
        } finally {
            recorderStarted = false
            isRecording = false

            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            recorder = null

            runCatching { virtualDisplay?.release() }
            virtualDisplay = null

            if (!fromProjectionCallback) {
                runCatching { projection?.stop() }
            }
            runCatching { projection?.unregisterCallback(projectionCallback) }
            projection = null

            runCatching { outputPfd?.close() }
            outputPfd = null

            val uri = outputUri
            if (uri != null) {
                if (hadStarted) {
                    runCatching {
                        contentResolver.update(
                            uri,
                            ContentValues().apply {
                                put(MediaStore.Video.Media.IS_PENDING, 0)
                            },
                            null,
                            null
                        )
                    }
                } else {
                    runCatching { contentResolver.delete(uri, null, null) }
                }
            }
            outputUri = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            sendStatus(
                STATE_STOPPED,
                if (userInitiated && hadStarted) {
                    getString(R.string.screen_stopped_saved)
                } else if (hadStarted) {
                    getString(R.string.screen_session_ended_saved)
                } else {
                    getString(R.string.screen_session_stopped)
                }
            )
            stopping = false
            stopSelf()
        }
    }

    private fun abortRecording(message: String) {
        if (stopping) return
        stopping = true
        val uri = outputUri
        recorderStarted = false
        isRecording = false

        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        runCatching { outputPfd?.close() }
        outputPfd = null
        if (uri != null) runCatching { contentResolver.delete(uri, null, null) }
        outputUri = null

        sendStatus(STATE_ERROR, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun chooseCaptureSize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val longSide = max(sourceWidth, sourceHeight).toFloat()
        val initialScale = min(1f, 1920f / longSide)

        var scale = initialScale
        repeat(10) {
            val width = even((sourceWidth * scale).roundToInt()).coerceAtLeast(320)
            val height = even((sourceHeight * scale).roundToInt()).coerceAtLeast(320)
            if (supportsH264(width, height)) return width to height
            scale *= 0.88f
        }

        val fallbackScale = min(1f, 1280f / longSide)
        return even((sourceWidth * fallbackScale).roundToInt()).coerceAtLeast(320) to
            even((sourceHeight * fallbackScale).roundToInt()).coerceAtLeast(320)
    }

    private fun supportsH264(width: Int, height: Int): Boolean {
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { it.isEncoder }
                .filter {
                    it.supportedTypes.any { type ->
                        type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                    }
                }
                .any { codec ->
                    val caps = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    caps.videoCapabilities.isSizeSupported(width, height)
                }
        }.getOrDefault(false)
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun notification(text: String): Notification {
        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop_save), stopPending)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.screen_record_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun sendStatus(state: String, message: String) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    override fun onDestroy() {
        if (recorder != null || isRecording) {
            finishRecording(fromProjectionCallback = false, userInitiated = false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
