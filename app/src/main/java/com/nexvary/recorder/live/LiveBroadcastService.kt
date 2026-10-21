package com.nexvary.recorder.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexvary.recorder.MainActivity
import com.nexvary.recorder.R
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.rtmp.RtmpFromFile
import kotlin.concurrent.thread

class LiveBroadcastService : Service() {
    companion object {
        const val ACTION_START = "com.nexvary.recorder.live.START"
        const val ACTION_STOP = "com.nexvary.recorder.live.STOP"
        const val ACTION_STATUS = "com.nexvary.recorder.live.STATUS"
        const val EXTRA_URI = "uri"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_LOOP = "loop"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "live_broadcast"
        private const val NOTIFICATION_ID = 1301

        @Volatile var isRunning = false
            private set
    }

    private val session = BroadcastSession<RtmpFromFile>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
                val endpoint = intent.getStringExtra(EXTRA_ENDPOINT).orEmpty()
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                if (uri == null || endpoint.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification())
                }
                begin(uri, endpoint, loop)
            }
            ACTION_STOP -> stopBroadcast()
        }
        return START_NOT_STICKY
    }

    private fun begin(uri: Uri, endpoint: String, loop: Boolean) {
        val token = session.begin() ?: return
        isRunning = true
        sendStatus(getString(R.string.live_preparing))
        fun status(message: Int) { if (session.current(token)) sendStatus(getString(message)) }
        fun fail(message: String) { main.post { stopBroadcast(token, message) } }
        thread(name = "NexvaryLivePrepare") {
            var prepared: RtmpFromFile? = null
            try {
                val checker = object : ConnectChecker {
                    override fun onConnectionStarted(url: String) = status(R.string.live_connecting)
                    override fun onConnectionSuccess() = status(R.string.live_connected)
                    override fun onConnectionFailed(reason: String) = fail(getString(R.string.live_connection_failed_format, reason))
                    override fun onDisconnect() = fail(getString(R.string.live_disconnected))
                    override fun onAuthError() = fail(getString(R.string.live_auth_error))
                    override fun onAuthSuccess() = status(R.string.live_auth_success)
                }
                val video = object : VideoDecoderInterface {
                    override fun onVideoDecoderFinished() { if (!loop) fail(getString(R.string.live_stopped)) }
                }
                val audio = object : AudioDecoderInterface { override fun onAudioDecoderFinished() = Unit }
                val rtmp = RtmpFromFile(this, checker, video, audio)
                prepared = rtmp
                rtmp.setLoopMode(loop)
                val videoReady = rtmp.prepareVideo(this, uri, 4_500_000, 0)
                val audioReady = runCatching { rtmp.prepareAudio(this, uri, 128_000) }.getOrDefault(false)
                if (!videoReady) error(getString(R.string.live_video_unsupported))
                if (!audioReady) status(R.string.live_audio_unsupported)
                // Stop and committing start are serialized; a late preparation cannot restart a stopped service.
                if (!session.attach(token, rtmp) {
                        sendStatus(getString(R.string.live_connecting))
                        rtmp.startStream(endpoint)
                    }) runCatching { rtmp.stopStream() }
            } catch (error: Exception) {
                runCatching { prepared?.stopStream() }
                fail(getString(R.string.live_connection_failed_format, error.message ?: getString(R.string.live_video_unsupported)))
            }
        }
    }

    private fun stopBroadcast(token: Long? = null, message: String = getString(R.string.live_stopped)) {
        val stopped = session.stop(token)
        if (token != null && stopped == null) return // Ignore callbacks from an older stream.
        runCatching { stopped?.resource?.stopStream() }
        isRunning = false
        sendStatus(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendStatus(text: String) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, text)
        )
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, LiveBroadcastService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.live_notification_title))
            .setContentText(getString(R.string.live_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_broadcast),
                stop
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.live_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        val stopped = session.stop()
        runCatching { stopped?.resource?.stopStream() }
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
