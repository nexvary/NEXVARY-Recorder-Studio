package com.nexvary.recorder.media

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.nexvary.recorder.R
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingsLibraryActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var refresh: Button
    private data class Recording(val name: String, val uri: android.net.Uri, val mime: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.library_title)
        refresh = Button(this).apply { setText(R.string.refresh); setOnClickListener { load() } }
        root.addView(refresh)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        load()
    }

    private fun load() {
        refresh.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readRecordings() } }
            list.removeAllViews()
            result.onSuccess { recordings ->
                if (recordings.isEmpty()) list.addView(TextView(this@RecordingsLibraryActivity).apply {
                    setText(R.string.library_empty); setTextColor(0xFFE0E7EE.toInt()); textSize = 17f
                })
                recordings.forEach { recording ->
                    list.addView(Button(this@RecordingsLibraryActivity).apply {
                        text = recording.name; isAllCaps = false
                        setOnClickListener {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(recording.uri, recording.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }.onFailure { Toast.makeText(this@RecordingsLibraryActivity, R.string.open_media_failed, Toast.LENGTH_LONG).show() }
                        }
                    })
                }
            }.onFailure {
                list.addView(TextView(this@RecordingsLibraryActivity).apply { setText(R.string.library_access_error); setTextColor(0xFFFFFFFF.toInt()) })
            }
            refresh.isEnabled = true
        }
    }

    private fun readRecordings(): List<Recording> {
        val rows = mutableListOf<Recording>()
        for ((collection, mime) in listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "audio/*", MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/*")) {
            contentResolver.query(collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf("%/NEXVARY Recorder/%"), "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) rows.add(Recording(cursor.getString(1), ContentUris.withAppendedId(collection, cursor.getLong(0)), mime))
            }
        }
        getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.listFiles()?.filter { it.isFile && it.extension == "mp4" && it.length() > 0 }?.sortedByDescending { it.lastModified() }?.forEach {
            rows.add(Recording(it.name, FileProvider.getUriForFile(this, "$packageName.files", it), "video/mp4"))
        }
        return rows
    }
}
