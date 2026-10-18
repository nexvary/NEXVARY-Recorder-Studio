package com.nexvary.recorder.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexvary.recorder.R
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets

class BackgroundCameraActivity : AppCompatActivity() {
    private var front = false
    private var audio = false
    private var pendingStart = false
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24*density).toInt(), (32*density).toInt(), (24*density).toInt(), 0)
        }
        val title = TextView(this).apply {
            text = "Background Camera Recording"
            textSize = 22f
        }
        val description = TextView(this).apply {
            text = "Start while this screen is visible. Recording can continue when using other apps or locking the screen. Android camera privacy indicators and a Stop notification remain visible."
        }
        val camera = Switch(this).apply {
            text = "Use front camera"
            setOnCheckedChangeListener { _, checked -> front = checked }
        }
        val mic = Switch(this).apply {
            text = "Record microphone"
            setOnCheckedChangeListener { _, checked -> audio = checked }
        }
        val start = Button(this).apply {
            text = "Start camera recording"
            setOnClickListener { requestAndStart() }
        }
        val stop = Button(this).apply {
            text = "Stop and save"
            setOnClickListener {
                startService(Intent(this@BackgroundCameraActivity, BackgroundCameraService::class.java)
                    .setAction(BackgroundCameraService.ACTION_STOP))
            }
        }
        listOf(title, description, camera, mic, start, stop).forEach(root::addView)
        setContentView(root)
        UiInsets.apply(root)
    }

    private fun requestAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (audio) permissions.add(Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingStart = true
            requestPermissions(missing.toTypedArray(), 600)
            return
        }
        pendingStart = false
        runCatching {
            ContextCompat.startForegroundService(this,
                Intent(this, BackgroundCameraService::class.java)
                    .setAction(BackgroundCameraService.ACTION_START)
                    .putExtra(BackgroundCameraService.EXTRA_FRONT, front)
                    .putExtra(BackgroundCameraService.EXTRA_AUDIO, audio))
        }.onSuccess {
            Toast.makeText(this, "Camera start requested. Confirm recording via the system camera indicator.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Camera could not start: ${it.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 600 && pendingStart) {
            pendingStart = false
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                requestAndStart()
            } else {
                Toast.makeText(this, "Camera or microphone permission denied. Recording was not started.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
