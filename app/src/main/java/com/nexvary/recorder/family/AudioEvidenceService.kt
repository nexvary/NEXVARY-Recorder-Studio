package com.nexvary.recorder.family

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.*
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Started from the visible safety UI only; normal Android microphone indicator and notification stay visible. */
class AudioEvidenceService: Service() {
    private val executor=Executors.newSingleThreadExecutor()
    private var recorder: MediaRecorder?=null
    private var file: File?=null
    private var started=false
    private var requested=false
    private var event=""
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action == "STOP") { runCatching { executor.execute { close() } };return START_NOT_STICKY }
        if(requested) return START_NOT_STICKY
        event=intent?.getStringExtra("event").orEmpty()
        if(runCatching { UUID.fromString(event) }.isFailure || ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf();return START_NOT_STICKY }
        requested=true
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("family_audio",getString(R.string.fs_evidence),NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
        val stop=PendingIntent.getService(this,0,Intent(this,AudioEvidenceService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(this,"family_audio").setSmallIcon(R.drawable.ic_feature_screen).setContentTitle(getString(R.string.fs_evidence))
            .setContentText(getString(R.string.fs_audio_active)).setOngoing(true).addAction(0,getString(R.string.camera_stop),stop).build()
        try { if(Build.VERSION.SDK_INT >= 30) startForeground(3603,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(3603,notification) }
        catch(_: Exception) { stopSelf();return START_NOT_STICKY }
        val duration=intent?.getIntExtra("duration",0)?.coerceIn(0,86_400_000) ?: 0
        executor.execute {
            try {
                check(EvidenceManager.storageBytes(this) >= 20_000_000)
                val output=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"NEXVARY_AUDIO_${System.currentTimeMillis()}.m4a");file=output
                val media=MediaRecorder();recorder=media
                media.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1);setAudioSamplingRate(16_000);setAudioEncodingBitRate(32_000);setOutputFile(output.absolutePath)
                    if(duration > 0) setMaxDuration(duration)
                    setOnInfoListener { _,what,_ -> if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) runCatching { executor.execute { close() } } }
                    setOnErrorListener { _,_,_ -> runCatching { executor.execute { close() } } }
                    prepare();start()
                }
                started=true;activeEvent=event
                FamilyStore(this).update { EvidenceManager.action(it,event,"RECORDING");FamilyStore.event(it,"AUDIO_EVIDENCE_STARTED") }
            } catch(_: Exception) { close() }
        }
        return START_NOT_STICKY
    }
    private fun close() {
        val media=recorder ?: run { stopSelf();return };recorder=null
        val valid=started && runCatching { media.stop() }.isSuccess
        runCatching { media.release() }
        runCatching {
            val output=file
            if(valid && output != null && output.length()>0) EvidenceManager.finalized(this,output,event)
            else { output?.delete();FamilyStore(this).update { EvidenceManager.action(it,event,"FAILED");FamilyStore.event(it,"EVIDENCE_FAILED") } }
        }
        activeEvent=null;started=false
        Handler(mainLooper).post { stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    }
    override fun onDestroy() { runCatching { executor.execute { close() } };executor.shutdown();super.onDestroy() }
    companion object { @Volatile var activeEvent: String?=null;private set }
}
