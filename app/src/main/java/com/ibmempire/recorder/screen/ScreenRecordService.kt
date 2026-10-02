package com.ibmempire.recorder.screen

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
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.ibmempire.recorder.MainActivity
import com.ibmempire.recorder.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenRecordService : Service() {
    companion object {
        const val ACTION_START = "com.ibmempire.recorder.START_RECORD"
        const val ACTION_STOP = "com.ibmempire.recorder.STOP_RECORD"
        const val ACTION_PAUSE = "com.ibmempire.recorder.PAUSE_RECORD"
        const val ACTION_RESUME = "com.ibmempire.recorder.RESUME_RECORD"
        const val ACTION_STATUS = "com.ibmempire.recorder.RECORD_STATUS"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_MIC = "record_mic"
        const val EXTRA_FLOATING = "floating_control"
        const val EXTRA_CAMERA = "floating_camera"
        const val EXTRA_SHOW_TOUCHES = "show_touches"
        const val EXTRA_OUTPUT_TREE_URI = "output_tree_uri"

        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"

        const val STATE_STARTING = "starting"
        const val STATE_STARTED = "started"
        const val STATE_PAUSED = "paused"
        const val STATE_RESUMED = "resumed"
        const val STATE_STOPPED = "stopped"
        const val STATE_ERROR = "error"

        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 1201
        private const val TOO_SOON_MS = 2_500L

        @Volatile
        var isRecording: Boolean = false
            private set

        @Volatile
        var isPaused: Boolean = false
            private set
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: Uri? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null
    private var outputIsMediaStore = true
    private var recorderStarted = false
    private var stopping = false
    private var startedAtElapsedMs = 0L
    private var floatingOverlay: FloatingRecorderOverlay? = null
    private var floatingCameraOverlay: FloatingCameraOverlay? = null
    private var previousShowTouches: Int? = null
    private var cameraFeatureAvailable = false
    private var touchesEnabledNow = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            val duration = if (startedAtElapsedMs > 0L) {
                SystemClock.elapsedRealtime() - startedAtElapsedMs
            } else {
                0L
            }

            if (recorderStarted && duration in 1 until TOO_SOON_MS) {
                abortRecording(getString(R.string.screen_projection_stopped_immediately))
            } else {
                finishRecording(fromProjectionCallback = true, userInitiated = false)
            }
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
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
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

        val floatingRequested = intent.getBooleanExtra(EXTRA_FLOATING, true)
        val cameraRequested = intent.getBooleanExtra(EXTRA_CAMERA, false)
        val showTouchesRequested = intent.getBooleanExtra(EXTRA_SHOW_TOUCHES, false)
        val useCamera = cameraRequested &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        cameraFeatureAvailable = useCamera
        val outputTreeUri = intent.getStringExtra(EXTRA_OUTPUT_TREE_URI).orEmpty()

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
            var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= 30 && useMic) {
                foregroundType = foregroundType or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= 34 && useCamera) {
                foregroundType = foregroundType or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(getString(R.string.notification_recording_preparing)),
                    foregroundType
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification(getString(R.string.notification_recording_preparing))
                )
            }

            sendStatus(STATE_STARTING, getString(R.string.screen_preparing))

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val (width, height) =
                chooseCaptureSize(metrics.widthPixels, metrics.heightPixels)

            val bitrate = (width.toLong() * height.toLong() * 5L)
                .coerceIn(4_000_000L, 12_000_000L)
                .toInt()

            createOutput(outputTreeUri)
            val fd = outputPfd?.fileDescriptor
                ?: error(getString(R.string.screen_file_create_fail))

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

            projection!!.registerCallback(
                projectionCallback,
                Handler(Looper.getMainLooper())
            )

            virtualDisplay = projection!!.createVirtualDisplay(
                "IBMEmpireRecorder",
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
            isPaused = false
            startedAtElapsedMs = SystemClock.elapsedRealtime()

            if (showTouchesRequested && Settings.System.canWrite(this)) {
                previousShowTouches = runCatching {
                    Settings.System.getInt(contentResolver, "show_touches", 0)
                }.getOrDefault(0)
                runCatching {
                    Settings.System.putInt(contentResolver, "show_touches", 1)
                }
                touchesEnabledNow = true
            } else {
                touchesEnabledNow = false
            }

            if (floatingRequested) {
                floatingOverlay = FloatingRecorderOverlay(
                    this,
                    onPauseResumeRequested = {
                        startService(
                            Intent(this, ScreenRecordService::class.java).apply {
                                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
                            }
                        )
                    },
                    onCameraToggleRequested = {
                        toggleFloatingCamera()
                    },
                    onTouchesToggleRequested = {
                        toggleTouches()
                    },
                    onOpenRequested = {
                        startActivity(
                            Intent(this, ScreenRecorderActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                )
                            }
                        )
                    },
                    onStopRequested = {
                        startService(
                            Intent(this, ScreenRecordService::class.java).apply {
                                action = ACTION_STOP
                            }
                        )
                    }
                ).also {
                    it.show(startedAtElapsedMs)
                    it.updateCameraEnabled(useCamera)
                    it.updateTouchesEnabled(touchesEnabledNow)
                }
            }

            if (useCamera && Settings.canDrawOverlays(this)) {
                floatingCameraOverlay = FloatingCameraOverlay(this).also { it.show() }
            }

            val sizeLabel = "${width}×${height}"
            updateNotification(
                getString(R.string.recording_running)
            )
            sendStatus(
                STATE_STARTED,
                getString(R.string.screen_started_size_format, sizeLabel)
            )
        } catch (e: Exception) {
            abortRecording(
                e.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.recording_failed)
            )
        }
    }

    private fun createOutput(treeUriString: String) {
        val name =
            "screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"

        if (treeUriString.isNotBlank()) {
            val treeUri = Uri.parse(treeUriString)
            val directory = DocumentFile.fromTreeUri(this, treeUri)

            if (directory != null && directory.exists() && directory.canWrite()) {
                val document = directory.createFile("video/mp4", name)
                    ?: error(getString(R.string.screen_file_create_fail))

                outputUri = document.uri
                outputIsMediaStore = false
                outputPfd = contentResolver.openFileDescriptor(document.uri, "w")
                    ?: error(getString(R.string.screen_file_open_fail))
                return
            }
        }

        outputIsMediaStore = true
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/IBM Empire Recorder"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        outputUri =
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error(getString(R.string.screen_file_create_fail))

        outputPfd =
            contentResolver.openFileDescriptor(outputUri!!, "w")
                ?: error(getString(R.string.screen_file_open_fail))
    }

    private fun finishRecording(
        fromProjectionCallback: Boolean,
        userInitiated: Boolean
    ) {
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
            isPaused = false
            startedAtElapsedMs = 0L
            floatingOverlay?.hide()
            floatingOverlay = null
            floatingCameraOverlay?.release()
            floatingCameraOverlay = null
            restoreShowTouches()

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
                if (hadStarted && outputIsMediaStore) {
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
                } else if (!hadStarted) {
                    runCatching { contentResolver.delete(uri, null, null) }
                }
            }

            outputUri = null
            outputIsMediaStore = true

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
        isPaused = false
        startedAtElapsedMs = 0L
        floatingOverlay?.hide()
        floatingOverlay = null
        floatingCameraOverlay?.release()
        floatingCameraOverlay = null
        restoreShowTouches()

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

        if (uri != null) {
            runCatching { contentResolver.delete(uri, null, null) }
        }

        outputUri = null
        outputIsMediaStore = true

        sendStatus(STATE_ERROR, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopping = false
        stopSelf()
    }

    private fun pauseRecording() {
        if (!recorderStarted || !isRecording || isPaused) return

        runCatching { recorder?.pause() }
            .onSuccess {
                isPaused = true
                floatingOverlay?.updatePaused(true)
                updateNotification(getString(R.string.recording_paused))
                sendStatus(STATE_PAUSED, getString(R.string.recording_paused))
            }
            .onFailure {
                sendStatus(STATE_ERROR, it.message ?: getString(R.string.recording_failed))
            }
    }

    private fun resumeRecording() {
        if (!recorderStarted || !isRecording || !isPaused) return

        runCatching { recorder?.resume() }
            .onSuccess {
                isPaused = false
                floatingOverlay?.updatePaused(false)
                updateNotification(getString(R.string.recording_resumed))
                sendStatus(STATE_RESUMED, getString(R.string.recording_resumed))
            }
            .onFailure {
                sendStatus(STATE_ERROR, it.message ?: getString(R.string.recording_failed))
            }
    }

    private fun toggleFloatingCamera() {
        if (!cameraFeatureAvailable || !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, ScreenRecorderActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
            )
            return
        }

        if (floatingCameraOverlay != null) {
            floatingCameraOverlay?.release()
            floatingCameraOverlay = null
            floatingOverlay?.updateCameraEnabled(false)
        } else {
            floatingCameraOverlay = FloatingCameraOverlay(this).also { it.show() }
            floatingOverlay?.updateCameraEnabled(true)
        }
    }

    private fun toggleTouches() {
        if (!Settings.System.canWrite(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        }

        if (previousShowTouches == null) {
            previousShowTouches = runCatching {
                Settings.System.getInt(contentResolver, "show_touches", 0)
            }.getOrDefault(0)
        }

        touchesEnabledNow = !touchesEnabledNow
        runCatching {
            Settings.System.putInt(
                contentResolver,
                "show_touches",
                if (touchesEnabledNow) 1 else 0
            )
        }
        floatingOverlay?.updateTouchesEnabled(touchesEnabledNow)
    }

    private fun restoreShowTouches() {
        val previous = previousShowTouches
        previousShowTouches = null
        touchesEnabledNow = false
        if (previous != null && Settings.System.canWrite(this)) {
            runCatching {
                Settings.System.putInt(contentResolver, "show_touches", previous)
            }
        }
    }

    private fun chooseCaptureSize(
        sourceWidth: Int,
        sourceHeight: Int
    ): Pair<Int, Int> {
        val longSide = max(sourceWidth, sourceHeight).toFloat()
        val initialScale = min(1f, 1920f / longSide)

        var scale = initialScale

        repeat(10) {
            val width =
                even((sourceWidth * scale).roundToInt()).coerceAtLeast(320)
            val height =
                even((sourceHeight * scale).roundToInt()).coerceAtLeast(320)

            if (supportsH264(width, height)) return width to height
            scale *= 0.88f
        }

        val fallbackScale = min(1f, 1280f / longSide)

        return even((sourceWidth * fallbackScale).roundToInt())
            .coerceAtLeast(320) to
            even((sourceHeight * fallbackScale).roundToInt())
                .coerceAtLeast(320)
    }

    private fun supportsH264(width: Int, height: Int): Boolean {
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { it.isEncoder }
                .filter {
                    it.supportedTypes.any { type ->
                        type.equals(
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            ignoreCase = true
                        )
                    }
                }
                .any { codec ->
                    val caps =
                        codec.getCapabilitiesForType(
                            MediaFormat.MIMETYPE_VIDEO_AVC
                        )

                    caps.videoCapabilities?.isSizeSupported(width, height) == true
                }
        }.getOrDefault(false)
    }

    private fun even(value: Int): Int =
        if (value % 2 == 0) value else value - 1

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
        else MediaRecorder()

    private fun notification(text: String): Notification {
        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, ScreenRecorderActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenRecordService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .setContentIntent(openPending)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                getString(if (isPaused) R.string.resume_recording else R.string.pause_recording),
                pauseResumeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop_save),
                stopPending
            )
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
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
        floatingOverlay?.hide()
        floatingOverlay = null
        floatingCameraOverlay?.release()
        floatingCameraOverlay = null
        restoreShowTouches()

        if (recorder != null || isRecording) {
            finishRecording(
                fromProjectionCallback = false,
                userInitiated = false
            )
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
