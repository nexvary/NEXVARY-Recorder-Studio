package com.nexvary.recorder.calls

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.GuideActivity
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.ui.*

/** Separate companion integration; this page never claims to capture call audio itself. */
class CallRecorderActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var readiness: TextView
    private val companionPackage = "com.kitsumed.shizucallrecorder"
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.call_title)
        DeliveryUi.text(root, R.string.guide_a_calls)
        status = TextView(this).apply { textSize = 17f; root.addView(this) }
        readiness = TextView(this).apply { textSize = 16f; root.addView(this) }
        DeliveryUi.button(root, R.string.call_authorize) {
            val requested = CallCapturePreflight.requestAuthorization()
            Toast.makeText(this, if (requested) R.string.call_permission_prompt else R.string.call_start_shizuku, Toast.LENGTH_LONG).show()
            render()
        }
        FeatureCards.add(root, R.drawable.asset_audio, R.string.call_open) {
            val launch = packageManager.getLaunchIntentForPackage(companionPackage)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kitsumed/ShizuCallRecorder/releases"))
            runCatching { startActivity(launch) }.onFailure { Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show() }
        }
        FeatureCards.add(root, R.drawable.asset_info, R.string.guide_title) {
            startActivity(Intent(this, GuideActivity::class.java).putExtra("query", getString(R.string.guide_q_calls)))
        }
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val value = CallCapturePreflight.inspect(this)
        readiness.text = "Shizuku: " + getString(if (!value.shizukuRunning) R.string.call_start_shizuku else if (value.shizukuAuthorized) R.string.fs_permission_granted else R.string.fs_permission_denied)
        status.setText(if (packageManager.getLaunchIntentForPackage(companionPackage) != null) R.string.call_companion_installed else R.string.call_companion_missing)
    }
}
