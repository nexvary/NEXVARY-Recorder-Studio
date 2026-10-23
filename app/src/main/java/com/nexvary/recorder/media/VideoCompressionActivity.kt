package com.nexvary.recorder.media

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.FamilyUi
import com.nexvary.recorder.ui.*
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoCompressionActivity: AppCompatActivity() {
    private var transformer: Transformer?=null
    private var output: File?=null
    private lateinit var status: TextView
    private lateinit var choose: android.widget.Button
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && transformer==null) {
            val file=File(cacheDir,"compressed_${System.nanoTime()}.mp4");output=file;choose.isEnabled=false;status.setText(R.string.guardian_compressing)
            val factory=DefaultEncoderFactory.Builder(this).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(1_000_000).build()).build()
            transformer=Transformer.Builder(this).setEncoderFactory(factory).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object: Transformer.Listener {
                    override fun onCompleted(composition: Composition,result: ExportResult) {
                        transformer=null
                        FamilyUi.io(this@VideoCompressionActivity,{ runCatching { publish(file) }; }) { result ->
                            file.delete();output=null;choose.isEnabled=true;status.setText(if(result.isSuccess) R.string.guardian_exported else R.string.guardian_compress_failed)
                        }
                    }
                    override fun onError(composition: Composition,result: ExportResult,error: ExportException) {
                        transformer=null;file.delete();output=null;choose.isEnabled=true;status.setText(R.string.guardian_compress_failed)
                    }
                }).build()
            val item=EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(),listOf(Presentation.createForHeight(720)))).build()
            runCatching { transformer!!.start(item,file.absolutePath) }.onFailure { transformer?.cancel();transformer=null;file.delete();choose.isEnabled=true;status.setText(R.string.guardian_compress_failed) }
        }
    }
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        val root=StudioPage.install(this,R.string.guardian_compress_video)
        status=DeliveryUi.text(root,R.string.guardian_compress_note)
        choose=DeliveryUi.button(root,R.string.choose_video) { picker.launch(arrayOf("video/*")) }
        DeliveryUi.button(root,R.string.cancel) { transformer?.cancel();transformer=null;output?.delete();output=null;choose.isEnabled=true;status.setText(R.string.guardian_compress_note) }
    }
    private fun publish(file: File) {
        val uri=checkNotNull(contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"NEXVARY_compressed_${System.currentTimeMillis()}.mp4");put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4");put(MediaStore.MediaColumns.RELATIVE_PATH,"Movies/NEXVARY Recorder");put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        try { checkNotNull(contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } };check(contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)==1) }
        catch(error: Exception) { contentResolver.delete(uri,null,null);throw error }
    }
    override fun onDestroy() { transformer?.cancel();output?.delete();super.onDestroy() }
}
