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
    private val recorderPackage = "com.kitsumed.shizucallrecorder"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "Call Recording / تسجيل المكالمات"
            textSize = 22f
        }
        val info = TextView(this).apply {
            text = "ShizuCallRecorder companion integration. Requires separate installation, Shizuku setup and user permission. Recording both sides is device-dependent. This screen does not record calls itself. Obtain required consent before recording."
            textSize = 15f
        }
        val status = TextView(this).apply { textSize = 16f }
        val preflight = TextView(this).apply { textSize = 15f }
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
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        findViewById<LinearLayout>(android.R.id.content).let { content ->
            val root = content.getChildAt(0) as? LinearLayout ?: return
            val status = root.getChildAt(2) as? TextView ?: return
            val readiness = CallCapturePreflight.inspect(this)
            (root.getChildAt(3) as? TextView)?.text = "Shizuku installed: ${readiness.shizukuInstalled} | running: ${readiness.shizukuRunning} | authorized: ${readiness.shizukuAuthorized}"
            status.text = if (packageManager.getLaunchIntentForPackage(recorderPackage) != null)
                "Companion installed / التطبيق المساعد مثبت"
            else "Companion not installed / التطبيق المساعد غير مثبت"
        }
    }
}
