package com.nexvary.recorder.screen

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

/** A recorder stop callback alone does not prove that an MP4 was finalized. */
internal object ScreenRecordingOutput {
    fun publish(context: Context, uri: Uri, mediaStore: Boolean, started: Boolean): Boolean {
        val valid = started && runCatching {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0 &&
                    (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) > 0
            } finally { metadata.release() }
        }.getOrDefault(false)
        if (valid && (!mediaStore || runCatching {
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1
            }.getOrDefault(false))) return true
        runCatching { context.contentResolver.delete(uri, null, null) }
        return false
    }
}
