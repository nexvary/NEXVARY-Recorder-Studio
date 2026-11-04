package com.nexvary.recorder

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/** Public synthetic emulator artifacts survive UTP's post-test application uninstall. */
object ContinuousTestEvidence {
    fun write(context: Context, name: String, mime: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NEXVARYContinuousEvidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
}
