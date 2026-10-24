package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.family.GuardianGate
import com.nexvary.recorder.ui.FeatureCards
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager

/** A real, scrollable destination instead of the former text-only modal menu. */
class MoreActivity : AppCompatActivity() {
    private var appliedTheme = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemeManager.currentIndex(this)
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.nav_more)
        root.addView(android.widget.TextView(this).apply {
            setText(R.string.more_intro); textSize = 15f
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        })
        fun page(type: Class<out android.app.Activity>) = startActivity(Intent(this, type))
        FeatureCards.grid(root, listOf(
            FeatureCards.Item(R.id.moreGuide, R.drawable.asset_info, R.string.guide_title, R.string.more_guide_note) { page(GuideActivity::class.java) },
            FeatureCards.Item(R.id.moreSettings, R.drawable.asset_settings, R.string.settings_title, R.string.more_settings_note) { page(SettingsActivity::class.java) },
            FeatureCards.Item(R.id.morePrivileged, R.drawable.asset_tools, R.string.device_diagnostics_title, R.string.device_diagnostics_note) {
                GuardianGate.action(this) { page(com.nexvary.npse.ControlCenterActivity::class.java) }
            },
            FeatureCards.Item(R.id.moreMedia, R.drawable.asset_tools, R.string.media_tools_title, R.string.more_media_note) { page(com.nexvary.recorder.media.MediaCenterActivity::class.java) },
            FeatureCards.Item(R.id.moreLibrary, R.drawable.asset_library, R.string.library_title, R.string.more_library_note) { page(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java) },
            FeatureCards.Item(R.id.moreCamera, R.drawable.asset_camera, R.string.camera_title, R.string.more_camera_note) { page(com.nexvary.recorder.camera.BackgroundCameraActivity::class.java) },
            FeatureCards.Item(R.id.moreCalls, R.drawable.asset_audio, R.string.call_title, R.string.more_calls_note) { page(com.nexvary.recorder.calls.CallRecorderActivity::class.java) },
            FeatureCards.Item(R.id.moreEvidence, R.drawable.asset_camera, R.string.safety_title, R.string.more_evidence_note) { page(com.nexvary.recorder.safety.SafetyCaptureActivity::class.java) },
            FeatureCards.Item(R.id.moreFamily, R.drawable.asset_family, R.string.fs_title, R.string.more_family_note) { page(com.nexvary.recorder.family.FamilyHubActivity::class.java) },
            FeatureCards.Item(R.id.moreRescue, R.drawable.asset_sos, R.string.rescue_title, R.string.more_rescue_note, emergency = true) { page(com.nexvary.recorder.guardian.GuardianEmergencyActivity::class.java) },
            FeatureCards.Item(R.id.moreLanguage, R.drawable.asset_launcher, R.string.language_title, R.string.more_language_note) { GuardianGate.action(this) { page(LanguageActivity::class.java) } },
            FeatureCards.Item(R.id.moreTheme, R.drawable.asset_settings, R.string.choose_theme, R.string.more_theme_note) { GuardianGate.action(this) { chooseTheme() } },
            FeatureCards.Item(R.id.moreAbout, R.drawable.asset_info, R.string.about_title, R.string.more_about_note) { page(AboutActivity::class.java) }
        ))
    }
    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemeManager.currentIndex(this)) recreate()
    }
    private fun chooseTheme() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.choose_theme)
            .setSingleChoiceItems(ThemeManager.themeNameResIds().map { getString(it) }.toTypedArray(), ThemeManager.currentIndex(this)) { dialog, index ->
                dialog.dismiss()
                if (index != ThemeManager.currentIndex(this)) { ThemeManager.select(this, index); recreate() }
            }.setNegativeButton(R.string.cancel, null).show()
    }
}
