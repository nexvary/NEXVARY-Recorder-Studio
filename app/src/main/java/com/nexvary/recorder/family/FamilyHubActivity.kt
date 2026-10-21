package com.nexvary.recorder.family

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.guardian.*
import com.nexvary.recorder.ui.*

class FamilyHubActivity: AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        val root=StudioPage.install(this,R.string.fs_title)
        FeatureCards.add(root,R.drawable.asset_family,R.string.fs_check_start) { startActivity(Intent(this,FamilyDashboardActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_family,R.string.fs_contacts) { startActivity(Intent(this,TrustedContactsActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_family,R.string.fs_journey_start) { startActivity(Intent(this,FamilyDashboardActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_info,R.string.fs_profile) { startActivity(Intent(this,EmergencyProfileActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_camera,R.string.fs_evidence) { startActivity(Intent(this,GuardianEmergencyActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_library,R.string.fs_history) { startActivity(Intent(this,SafetyHistoryActivity::class.java)) }
        DeliveryUi.button(root,R.string.guardian_usage) { startActivity(Intent(this,CloudSettingsActivity::class.java)) }
        FeatureCards.add(root,R.drawable.asset_settings,R.string.settings_safety) { startActivity(Intent(this,FamilySettingsActivity::class.java)) }
        DeliveryUi.button(root,R.string.guardian_child_mode) { ChildMode.activate(this) }
    }
}
