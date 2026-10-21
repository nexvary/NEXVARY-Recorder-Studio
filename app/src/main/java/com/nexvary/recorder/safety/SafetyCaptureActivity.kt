package com.nexvary.recorder.safety

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.camera.BackgroundCameraService
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.live.LiveBroadcastActivity

/**
 * Emergency evidence entry point. No covert recording, no false remote-live status.
 * Recording must be started from a visible activity; OS privacy indicators remain.
 */
class SafetyCaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.nexvary.recorder.ui.ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val root = com.nexvary.recorder.ui.StudioPage.install(this, com.nexvary.recorder.R.string.safety_title)
        val title = TextView(this).apply {
            text = "حماية وتوثيق الطوارئ / Safety Evidence"
            textSize = 23f
        }
        val note = TextView(this).apply {
            text = "ابدأ التسجيل قبل قفل الشاشة. تسجيل الكاميرا المحلي متاح؛ البث المباشر الحالي يرسل ملفات فيديو ولا يرسل صورة الكاميرا بعد. لا تعتمد على هذا التطبيق وحده لطلب النجدة. ستظل مؤشرات الخصوصية وإشعارات التسجيل ظاهرة."
            textSize = 16f
        }
        val local = Button(this).apply {
            text = "بدء تسجيل الكاميرا / Start camera evidence"
            setOnClickListener { startActivity(Intent(this@SafetyCaptureActivity, BackgroundCameraActivity::class.java)) }
        }
        val live = Button(this).apply {
            text = "البث الحالي (ملف فيديو) / Existing file broadcast"
            setOnClickListener { startActivity(Intent(this@SafetyCaptureActivity, LiveBroadcastActivity::class.java)) }
        }
        val stop = Button(this).apply {
            text = "إيقاف وحفظ التسجيل / Stop and save"
            setOnClickListener {
                startService(Intent(this@SafetyCaptureActivity, BackgroundCameraService::class.java)
                    .setAction(BackgroundCameraService.ACTION_STOP))
            }
        }
        val emergency = TextView(this).apply {
            text = "في خطر مباشر: اتصل بخدمات الطوارئ المحلية أو بشخص موثوق فورًا."
            textSize = 15f
        }
        listOf(title, note, local, live, stop, emergency).forEach(root::addView)
    }
}
