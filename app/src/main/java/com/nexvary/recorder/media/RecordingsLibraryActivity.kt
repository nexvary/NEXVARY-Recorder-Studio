package com.nexvary.recorder.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingsLibraryActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var refresh: Button
    private lateinit var vaultButton: Button
    private lateinit var vault: VaultStore
    private var session: VaultStore.Session? = null
    private var working = false
    private var started = false
    private var generation = 0
    private var player: VideoView? = null
    private var playerDialog: androidx.appcompat.app.AlertDialog? = null
    private var prompt: androidx.appcompat.app.AlertDialog? = null
    private val playbackDir by lazy { File(cacheDir, "vault_playback").apply { mkdirs() } }
    private data class Recording(val name: String, val uri: Uri, val mime: String, val file: File? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        vault = VaultStore(this)
        // Clean an interrupted preview after process death. Never expose this path via FileProvider.
        playbackDir.listFiles()?.forEach { it.delete() }
        val root = StudioPage.install(this, R.string.library_title)
        refresh = Button(this).apply { setText(R.string.refresh); setOnClickListener { load() } }
        root.addView(refresh)
        vaultButton = Button(this).apply {
            id = R.id.openVault; setText(R.string.vault_open)
            setOnClickListener { if (!working) { if (session == null) askPin() else { lock(); load() } } }
        }
        root.addView(vaultButton)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
    }
    override fun onStart() {
        super.onStart(); started = true
        if (session == null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        load()
    }
    override fun onStop() {
        started = false
        prompt?.dismiss(); prompt = null
        lock()
        super.onStop()
    }
    private fun lock() {
        session = null; generation++
        player?.stopPlayback(); player = null
        playerDialog?.dismiss(); playerDialog = null
        playbackDir.listFiles()?.forEach { it.delete() }
        if (::list.isInitialized) list.removeAllViews()
        if (::vaultButton.isInitialized) vaultButton.setText(R.string.vault_open)
        // FLAG_SECURE stays until onStart to prevent a private recents snapshot.
    }
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
    private fun askPin(after: (() -> Unit)? = null) {
        if (session != null) { after?.invoke(); return }
        val creating = !vault.configured
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 8) }
        form.addView(TextView(this).apply { setText(if (creating) R.string.vault_notice else R.string.vault_enter_note); setTextColor(0xFFF5F7FA.toInt()) })
        fun pinField(hint: Int, ident: Int) = EditText(this).apply {
            id = ident; setHint(hint); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(12))
            setTextColor(0xFF43FF88.toInt()); setHintTextColor(0xFF9BF6B8.toInt()); setSaveEnabled(false)
            form.addView(this)
        }
        val pin = pinField(R.string.vault_pin, R.id.vaultPin)
        val confirmation = if (creating) pinField(R.string.vault_confirm_pin, R.id.vaultConfirmPin) else null
        prompt = MaterialAlertDialogBuilder(this).setTitle(if (creating) R.string.vault_create else R.string.vault_open)
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.vault_unlock, null).create().apply {
                setOnShowListener {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    getButton(-1).setOnClickListener {
                        fun normalized(field: EditText) = field.text.toString().map { c -> Character.digit(c, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: c }.joinToString("")
                        val value = normalized(pin)
                        if (value.length !in 6..12 || value.any { it !in '0'..'9' } || (creating && value != confirmation?.let(::normalized))) {
                            pin.error = getString(R.string.vault_pin_invalid); return@setOnClickListener
                        }
                        val characters = value.toCharArray()
                        pin.text.clear(); confirmation?.text?.clear()
                        getButton(-1).isEnabled = false
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try { runCatching { if (creating) vault.create(characters) else vault.unlock(characters) } }
                                finally { characters.fill('\u0000') }
                            }
                            if (!started || !isShowing) return@launch
                            getButton(-1).isEnabled = true
                            val unlocked = result.getOrNull()
                            if (unlocked == null) pin.error = getString(R.string.vault_pin_failed)
                            else {
                                session = unlocked
                                this@RecordingsLibraryActivity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                dismiss(); vaultButton.setText(R.string.vault_lock)
                                vaultButton.post {
                                    if (started && session === unlocked) {
                                        if (after != null) after() else load()
                                    }
                                }
                            }
                        }
                    }
                }; show()
            }
    }
    private fun load() {
        if (working || !started) return
        val token = ++generation; val access = session
        refresh.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { if (access == null) readRecordings() else vault.entries(access) } }
            if (token != generation || !started) return@launch
            list.removeAllViews()
            result.onSuccess { recordings ->
                if (recordings.isEmpty()) list.addView(TextView(this@RecordingsLibraryActivity).apply {
                    setText(if (access == null) R.string.library_empty else R.string.vault_empty)
                })
                recordings.forEach { item ->
                    when (item) { is Recording -> publicRow(item); is VaultStore.Entry -> privateRow(item); else -> Unit }
                }
            }.onFailure { toast(R.string.library_access_error) }
            refresh.isEnabled = true
        }
    }
    private fun panel(name: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad); setBackgroundResource(R.drawable.bg_metal_card)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad }
        addView(TextView(this@RecordingsLibraryActivity).apply { text = name; textSize = 17f; setTextIsSelectable(true) })
        list.addView(this)
    }
    private fun LinearLayout.action(label: Int, click: () -> Unit) { addView(Button(this@RecordingsLibraryActivity).apply { setText(label); setOnClickListener { if (!working) click() } }) }
    private fun publicRow(recording: Recording) {
        panel(recording.name).apply {
            action(R.string.library_play) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(recording.uri, recording.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    .onFailure { toast(R.string.open_media_failed) }
            }
            action(R.string.library_delete) { confirm(R.string.library_delete_confirm) { operation { deleteOriginal(recording) } } }
            if (recording.mime.startsWith("video/")) action(R.string.library_hide) {
                askPin { confirm(R.string.vault_hide_confirm) {
                    val access = session ?: return@confirm
                    operation {
                        val entry = contentResolver.openInputStream(recording.uri)!!.use { vault.import(access, recording.name, "video/mp4", it) }
                        try { deleteOriginal(recording) } catch (e: Exception) { vault.delete(entry); throw e }
                    }
                } }
            }
        }
    }
    private fun privateRow(entry: VaultStore.Entry) {
        panel(entry.name).apply {
            action(R.string.library_play) { playPrivate(entry) }
            action(R.string.vault_restore) { confirm(R.string.vault_restore_confirm) { val access = session ?: return@confirm; operation { restore(access, entry) } } }
            action(R.string.library_delete) { confirm(R.string.library_delete_confirm) { operation { vault.delete(entry) } } }
        }
    }
    private fun confirm(message: Int, action: () -> Unit) {
        prompt = MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(R.string.vault_continue) { _, _ -> action() }.setNegativeButton(R.string.cancel, null).show()
        if (session != null) prompt?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun operation(action: () -> Unit) {
        if (working) return
        working = true; generation++; refresh.isEnabled = false; vaultButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            working = false; refresh.isEnabled = true; vaultButton.isEnabled = true
            if (started) { toast(if (result.isSuccess) R.string.library_done else R.string.library_operation_failed); load() }
        }
    }
    private fun deleteOriginal(r: Recording) {
        if (r.file != null) {
            check(r.file.absolutePath != com.nexvary.recorder.camera.BackgroundCameraService.activeOutputPath)
            check(r.file.delete())
        } else check(contentResolver.delete(r.uri, null, null) == 1)
    }
    private fun restore(access: VaultStore.Session, entry: VaultStore.Entry) {
        val video = entry.mime.startsWith("video/")
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = checkNotNull(contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.name.substringAfterLast('/'))
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/NEXVARY Recorder/" else "Music/NEXVARY Recorder/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            checkNotNull(contentResolver.openOutputStream(uri)).use { vault.export(access, entry, it) }
            check(contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
        vault.delete(entry)
    }
    private fun playPrivate(entry: VaultStore.Entry) {
        val access = session ?: return
        if (working) return
        working = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(playbackDir, "preview.mp4")
                    try { temp.outputStream().use { vault.export(access, entry, it) }; temp }
                    catch (e: Exception) { temp.delete(); throw e }
                }
            }
            working = false
            val temp = result.getOrNull()
            if (session !== access || !started) { temp?.delete(); return@launch }
            if (temp == null) { toast(R.string.open_media_failed); return@launch }
            val video = VideoView(this@RecordingsLibraryActivity)
            player = video
            video.setMediaController(MediaController(this@RecordingsLibraryActivity).apply { setAnchorView(video) })
            video.setOnErrorListener { _, _, _ -> toast(R.string.open_media_failed); playerDialog?.dismiss(); true }
            video.setVideoPath(temp.absolutePath)
            playerDialog = MaterialAlertDialogBuilder(this@RecordingsLibraryActivity).setTitle(entry.name).setView(video)
                .setNegativeButton(R.string.back, null).create().apply {
                    setOnDismissListener { video.stopPlayback(); temp.delete(); player = null }
                    show(); window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            video.layoutParams = android.widget.FrameLayout.LayoutParams(-1, (300 * resources.displayMetrics.density).toInt())
            video.start()
        }
    }
    private fun readRecordings(): List<Recording> {
        val rows = mutableListOf<Recording>()
        for ((collection, fallback) in listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "audio/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/mp4")) {
            contentResolver.query(collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf("%/NEXVARY Recorder/%"), "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) rows.add(Recording(cursor.getString(1), ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(2) ?: fallback))
            }
        }
        getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.listFiles()?.filter { it.isFile && it.extension == "mp4" && it.length() > 0 && it.absolutePath != com.nexvary.recorder.camera.BackgroundCameraService.activeOutputPath }?.sortedByDescending { it.lastModified() }?.forEach {
            rows.add(Recording(it.name, FileProvider.getUriForFile(this, "$packageName.files", it), "video/mp4", it))
        }
        return rows
    }
}
