package com.nexvary.recorder.safety

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.GuideActivity
import com.nexvary.recorder.R
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.camera.BackgroundCameraService
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.live.LiveBroadcastActivity
import com.nexvary.recorder.ui.*

/** Local evidence and file broadcast are distinct, user-started operations. */
class SafetyCaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.safety_title)
        DeliveryUi.text(root, R.string.camera_note)
        DeliveryUi.text(root, R.string.guide_a_sos)
        FeatureCards.add(root, R.drawable.asset_camera, R.string.camera_start) { startActivity(Intent(this, BackgroundCameraActivity::class.java)) }
        FeatureCards.add(root, R.drawable.asset_live, R.string.live_title) { startActivity(Intent(this, LiveBroadcastActivity::class.java)) }
        DeliveryUi.button(root, R.string.camera_stop) {
            if (BackgroundCameraService.activeOutputPath != null) startService(Intent(this, BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_STOP))
        }
        FeatureCards.add(root, R.drawable.asset_info, R.string.guide_title) { startActivity(Intent(this, GuideActivity::class.java).putExtra("query", getString(R.string.guide_q_evidence))) }
    }
}
