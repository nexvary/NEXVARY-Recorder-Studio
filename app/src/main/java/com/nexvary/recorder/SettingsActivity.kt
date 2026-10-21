package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager

class SettingsActivity : com.nexvary.recorder.family.GuardianProtectedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.settings_title)
        com.nexvary.recorder.ui.FeatureCards.add(root, R.drawable.asset_info, R.string.guide_title) { startActivity(Intent(this, GuideActivity::class.java)) }
        com.nexvary.recorder.delivery.DeliveryUi.button(root, R.string.fs_title, R.id.fsOpenDashboard) { startActivity(Intent(this, com.nexvary.recorder.family.FamilyDashboardActivity::class.java)) }
        com.nexvary.recorder.delivery.DeliveryUi.button(root, R.string.guardian_cloud) { startActivity(Intent(this, com.nexvary.recorder.guardian.CloudSettingsActivity::class.java)) }
        com.nexvary.recorder.delivery.DeliveryUi.button(root, R.string.guardian_child_mode) { com.nexvary.recorder.guardian.ChildMode.activate(this) }
        if (BuildConfig.DEBUG) com.nexvary.recorder.delivery.DeliveryUi.button(root, R.string.smtp_title, R.id.openSmtp) {
            startActivity(Intent(this, com.nexvary.recorder.delivery.SmtpSettingsActivity::class.java))
        }
        com.nexvary.recorder.delivery.DeliveryUi.button(root, R.string.mail_outbox, R.id.mailOutbox) {
            startActivity(Intent(this, com.nexvary.recorder.delivery.MailOutboxActivity::class.java))
        }
        root.addView(Button(this).apply {
            setText(R.string.language_title)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, LanguageActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            setText(R.string.choose_theme)
            setOnClickListener {
                MaterialAlertDialogBuilder(this@SettingsActivity).setTitle(R.string.choose_theme)
                    .setSingleChoiceItems(ThemeManager.themeNameResIds().map { getString(it) }.toTypedArray(), ThemeManager.currentIndex(this@SettingsActivity)) { dialog, index ->
                        ThemeManager.select(this@SettingsActivity, index); dialog.dismiss(); recreate()
                    }.setNegativeButton(R.string.cancel, null).show()
            }
        })
    }
}
