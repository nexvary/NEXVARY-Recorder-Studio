package com.nexvary.recorder.calls

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Companion integration only: no upstream GPL source is embedded.
 * Never misrepresent an external recorder as an in-app recording engine.
 */
class CallRecorderActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var preflightText: TextView
    private val recorderPackage = "com.kitsumed.shizucallrecorder"

    override fun onCreate(savedInstanceState: Bundle?) {
        com.nexvary.recorder.ui.ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val layout = com.nexvary.recorder.ui.StudioPage.install(this, com.nexvary.recorder.R.string.call_title)
        val title = TextView(this).apply {
            text = "Call Recording / تسجيل المكالمات"
            textSize = 22f
        }
        val info = TextView(this).apply {
            text = "ShizuCallRecorder companion integration. Requires separate installation, Shizuku setup and user permission. Recording both sides is device-dependent. This screen does not record calls itself. Obtain required consent before recording."
            textSize = 15f
        }
        val status = TextView(this).apply { textSize = 16f }.also { statusText = it }
        val preflight = TextView(this).apply { textSize = 15f }.also { preflightText = it }
        val authorize = Button(this).apply {
            text = "Authorize Shizuku / منح إذن Shizuku"
            setOnClickListener {
                val requested = CallCapturePreflight.requestAuthorization()
                Toast.makeText(this@CallRecorderActivity, if (requested) "Check Shizuku permission dialog" else "Start Shizuku first", Toast.LENGTH_LONG).show()
            }
        }
        val open = Button(this).apply {
            text = "Open ShizuCallRecorder / فتح مسجل المكالمات"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(recorderPackage)
                if (launch != null) startActivity(launch)
                else startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/kitsumed/ShizuCallRecorder/releases")))
            }
        }
        val help = Button(this).apply {
            text = "Setup instructions / تعليمات الإعداد"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/kitsumed/ShizuCallRecorder#readme")))
            }
        }
        listOf(title, info, status, preflight, authorize, open, help).forEach(layout::addView)
    }

    override fun onResume() {
        super.onResume()
        val readiness = CallCapturePreflight.inspect(this)
        preflightText.text = "Shizuku installed: ${readiness.shizukuInstalled} | running: ${readiness.shizukuRunning} | authorized: ${readiness.shizukuAuthorized}"
        statusText.text = if (packageManager.getLaunchIntentForPackage(recorderPackage) != null)
            "Companion installed / التطبيق المساعد مثبت"
        else "Companion not installed / التطبيق المساعد غير مثبت"
    }
}
