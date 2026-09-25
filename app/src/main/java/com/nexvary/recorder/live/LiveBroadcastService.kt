package com.nexvary.recorder.live

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexvary.recorder.MainActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.rtmp.RtmpFromFile
import kotlin.concurrent.thread

class LiveBroadcastService : Service(), ConnectChecker, VideoDecoderInterface, AudioDecoderInterface {
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

    private var streamer: RtmpFromFile? = null
    private var loopEnabled = false

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
                if (uri == null || endpoint.isBlank()) { stopSelf(); return START_NOT_STICKY }
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                else startForeground(NOTIFICATION_ID, notification())
                begin(uri, endpoint, loop)
            }
            ACTION_STOP -> stopBroadcast()
        }
        return START_NOT_STICKY
    }

    private fun begin(uri: Uri, endpoint: String, loop: Boolean) {
        if (isRunning) return
        isRunning = true
        loopEnabled = loop
        sendStatus("تجهيز الفيديو للبث…")
        thread(name = "NexvaryLivePrepare") {
            try {
                val rtmp = RtmpFromFile(this, this, this, this)
                streamer = rtmp
                rtmp.setLoopMode(loop)
                val videoReady = rtmp.prepareVideo(this, uri, 4_500_000, 0)
                val audioReady = runCatching { rtmp.prepareAudio(this, uri, 128_000) }.getOrDefault(false)
                if (!videoReady) error("تعذر تجهيز مسار الفيديو. استخدم MP4/H.264")
                if (!audioReady) sendStatus("تم تجهيز الفيديو؛ الصوت غير مدعوم وسيتم البث بدونه")
                sendStatus("الاتصال بخادم البث…")
                rtmp.startStream(endpoint)
            } catch (e: Exception) {
                sendStatus("فشل بدء البث: ${e.message}")
                stopBroadcast()
            }
        }
    }

    private fun stopBroadcast() {
        val rtmp = streamer
        streamer = null
        if (rtmp != null) runCatching { if (rtmp.isStreaming) rtmp.stopStream() }
        isRunning = false
        sendStatus("تم إيقاف البث")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onConnectionStarted(url: String) = sendStatus("جارٍ الاتصال…")
    override fun onConnectionSuccess() = sendStatus("البث متصل ويعمل الآن")
    override fun onConnectionFailed(reason: String) {
        sendStatus("فشل الاتصال: $reason")
        isRunning = false
    }
    override fun onDisconnect() { isRunning = false; sendStatus("انقطع/انتهى البث") }
    override fun onAuthError() = sendStatus("خطأ في Stream Key أو المصادقة")
    override fun onAuthSuccess() = sendStatus("تم قبول بيانات البث")
    override fun onVideoDecoderFinished() {
        val rtmp = streamer ?: return
        if (!rtmp.isStreaming || loopEnabled) return
        stopBroadcast()
    }
    override fun onAudioDecoderFinished() = Unit

    private fun sendStatus(text: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, text))
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 2, Intent(this, LiveBroadcastService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("NEXVARY Live Broadcaster")
            .setContentText("بث فيديو محفوظ جارٍ الآن")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف البث", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live broadcast", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        if (streamer != null) runCatching { streamer?.stopStream() }
        streamer = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
