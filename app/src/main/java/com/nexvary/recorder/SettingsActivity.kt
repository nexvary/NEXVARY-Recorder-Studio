package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.*
import com.nexvary.recorder.ui.*

class SettingsActivity : GuardianProtectedActivity() {
    override val screenshotsAllowed = true
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val appearance = intent.getBooleanExtra("appearance", false)
        val root = StudioPage.install(this, if (appearance) R.string.settings_appearance else R.string.settings_title)
        fun page(type: Class<out android.app.Activity>) = startActivity(Intent(this, type))
        if (appearance) {
            FeatureCards.add(root, R.drawable.asset_launcher, R.string.language_title) { page(LanguageActivity::class.java) }
            FeatureCards.add(root, R.drawable.asset_settings, R.string.choose_theme) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.choose_theme)
                    .setSingleChoiceItems(ThemeManager.themeNameResIds().map { getString(it) }.toTypedArray(), ThemeManager.currentIndex(this)) { dialog, index ->
                        ThemeManager.select(this, index); dialog.dismiss(); recreate()
                    }.setNegativeButton(R.string.cancel, null).show()
            }
            return
        }
        DeliveryUi.text(root, R.string.settings_intro)
        FeatureCards.grid(root, listOf(
            FeatureCards.Item(R.id.settingsSafety, R.drawable.asset_family, R.string.settings_safety, R.string.settings_safety_note) { page(FamilySettingsActivity::class.java) },
            FeatureCards.Item(R.id.settingsAppearance, R.drawable.asset_settings, R.string.settings_appearance, R.string.settings_appearance_note) { startActivity(Intent(this, SettingsActivity::class.java).putExtra("appearance", true)) },
            FeatureCards.Item(R.id.settingsAccount, R.drawable.asset_library, R.string.settings_account, R.string.settings_account_note) { page(com.nexvary.recorder.guardian.CloudSettingsActivity::class.java) },
            FeatureCards.Item(R.id.settingsHelp, R.drawable.asset_info, R.string.guide_title, R.string.more_guide_note) { page(GuideActivity::class.java) }
        ))
        val advanced = SettingsSections.add(root, R.string.settings_advanced, R.id.settingsAdvanced)
        DeliveryUi.text(advanced, R.string.settings_advanced_note)
        FeatureCards.add(advanced, R.drawable.asset_library, R.string.fs_queue) { page(SafetyQueueActivity::class.java) }
        FeatureCards.add(advanced, R.drawable.asset_library, R.string.mail_outbox) { page(com.nexvary.recorder.delivery.MailOutboxActivity::class.java) }
        if (BuildConfig.DEBUG) DeliveryUi.button(advanced, R.string.smtp_title, R.id.openSmtp) { page(com.nexvary.recorder.delivery.SmtpSettingsActivity::class.java) }
    }
}
