package com.nexvary.recorder.media

import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.family.FamilyUi
import com.nexvary.recorder.ui.*
import java.io.File
import java.nio.ByteBuffer

class MediaCenterActivity: AppCompatActivity() {
    private var operation="info"
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) when(operation) { "extract" -> extract(uri);"share" -> share(uri);else -> info(uri) }
    }
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        operation=saved?.getString("operation") ?: "info"
        val root=StudioPage.install(this,R.string.media_tools_title)
        FeatureCards.add(root,R.drawable.asset_audio,R.string.guardian_record_audio) { startActivity(Intent(this,com.nexvary.recorder.audio.VoiceRecorderActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_tools,R.string.convert_video) { tools("convert") }
        FeatureCards.add(root,R.drawable.asset_tools,R.string.guardian_compress_video) { startActivity(Intent(this,VideoCompressionActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_audio,R.string.guardian_extract_audio) { choose("extract",arrayOf("video/*")) }
        FeatureCards.add(root,R.drawable.asset_tools,R.string.replace_audio_title) { startActivity(Intent(this,AudioReplaceActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_tools,R.string.trim_video) { tools("trim") }
        FeatureCards.add(root,R.drawable.asset_audio,R.string.guardian_compress_audio) { tools("audio") }
        FeatureCards.add(root,R.drawable.asset_info,R.string.guardian_file_info) { choose("info",arrayOf("video/*","audio/*")) }
        FeatureCards.add(root,R.drawable.asset_library,R.string.guardian_share_export) { choose("share",arrayOf("video/*","audio/*")) }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("operation",operation);super.onSaveInstanceState(out) }
    private fun tools(tool: String)=startActivity(Intent(this,MediaToolsActivity::class.java).putExtra("tool",tool))
    private fun choose(action: String,mimes: Array<String>) { operation=action;picker.launch(mimes) }
    private fun share(uri: Uri) {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(contentResolver.getType(uri) ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=android.content.ClipData.newUri(contentResolver,"media",uri) },getString(R.string.guardian_share_export))) }
            .onFailure { Toast.makeText(this,R.string.rescue_share_error,Toast.LENGTH_LONG).show() }
    }
    private fun info(uri: Uri) {
        FamilyUi.io(this,{
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(this,uri)
                val keys=listOf("Duration (ms)" to MediaMetadataRetriever.METADATA_KEY_DURATION,"Width" to MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,"Height" to MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,"Bitrate (bits/s)" to MediaMetadataRetriever.METADATA_KEY_BITRATE,"MIME" to MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                keys.mapNotNull { (label,key) -> retriever.extractMetadata(key)?.let { "$label: $it" } }.joinToString("\n")
            } finally { retriever.release() }
        }) { details -> MaterialAlertDialogBuilder(this).setTitle(R.string.guardian_file_info).setMessage(details).setPositiveButton(R.string.back,null).show() }
    }
    private fun extract(uri: Uri) {
        FamilyUi.io(this,{
            val temp=File(cacheDir,"extract_${System.nanoTime()}.m4a")
            val extractor=MediaExtractor();var muxer: MediaMuxer?=null;var started=false
            try {
                extractor.setDataSource(this,uri,null)
                val index=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: error("NO_AUDIO_TRACK")
                val format=extractor.getTrackFormat(index);extractor.selectTrack(index)
                muxer=MediaMuxer(temp.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val track=muxer.addTrack(format);muxer.start();started=true
                val buffer=ByteBuffer.allocateDirect(maxOf(1024*1024,if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0))
                val info=MediaCodec.BufferInfo()
                while(true) {
                    buffer.clear();val size=extractor.readSampleData(buffer,0);if(size<0) break
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0) { "Encrypted audio cannot be exported" }
                    var flags=if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME!=0) flags=flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    info.set(0,size,extractor.sampleTime,flags);muxer.writeSampleData(track,buffer,info);extractor.advance()
                }
                muxer.stop();started=false;muxer.release();muxer=null
                publish(temp,"audio/mp4","m4a")
            } finally { if(started) runCatching { muxer?.stop() };muxer?.release();extractor.release();temp.delete() }
        }) { Toast.makeText(this,R.string.guardian_exported,Toast.LENGTH_LONG).show() }
    }
    internal fun publish(file: File,mime: String,extension: String) {
        val values=ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,"NEXVARY_${System.currentTimeMillis()}.$extension");put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.MediaColumns.RELATIVE_PATH,if(mime.startsWith("audio/")) Environment.DIRECTORY_MUSIC+"/NEXVARY" else Environment.DIRECTORY_MOVIES+"/NEXVARY");put(MediaStore.MediaColumns.IS_PENDING,1) }
        val uri=checkNotNull(contentResolver.insert(if(mime.startsWith("audio/")) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values))
        try { checkNotNull(contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } };contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null) }
        catch(error: Exception) { contentResolver.delete(uri,null,null);throw error }
    }
}
