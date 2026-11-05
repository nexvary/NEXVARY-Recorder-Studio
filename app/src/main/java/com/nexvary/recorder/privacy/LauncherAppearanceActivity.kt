package com.nexvary.recorder.privacy

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.GuardianGate
import com.nexvary.recorder.family.GuardianProtectedActivity
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager

class LauncherAppearanceActivity : GuardianProtectedActivity() {
    override val screenshotsAllowed = true
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        val root = StudioPage.install(this, R.string.launcher_title)
        DeliveryUi.text(root, R.string.launcher_intro)
        DeliveryUi.text(root, R.string.launcher_limits)
        val appearance = LauncherAppearance(this)
        val styles = LauncherAppearance.Style.entries
        val labels = listOf(R.string.app_name, R.string.launcher_clock, R.string.launcher_calendar)
        val icons = listOf(R.mipmap.ic_launcher, R.drawable.ic_launcher_clock, R.drawable.ic_launcher_calendar)
        val ids = listOf(R.id.launcherOriginal, R.id.launcherClock, R.id.launcherCalendar)
        val status = DeliveryUi.text(root, R.string.launcher_title).apply { id = R.id.launcherStatus }
        fun refresh() { status.text = getString(R.string.launcher_current, getString(labels[styles.indexOf(appearance.current())])) }
        val group = RadioGroup(this).apply { id = R.id.launcherChoices }
        styles.forEachIndexed { index, _ ->
            group.addView(RadioButton(this).apply {
                id = ids[index]; setText(labels[index]); textSize = 18f
                val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@LauncherAppearanceActivity, icons[index])!!
                val size = (40 * resources.displayMetrics.density).toInt(); icon.setBounds(0, 0, size, size)
                setCompoundDrawablesRelative(null, null, icon, null)
                minHeight = (64 * resources.displayMetrics.density).toInt()
            })
        }
        root.addView(group); group.check(ids[styles.indexOf(appearance.current())]); refresh()
        DeliveryUi.button(root, R.string.launcher_apply, R.id.launcherApply) {
            GuardianGate.action(this) {
                val index = ids.indexOf(group.checkedRadioButtonId)
                if (index < 0) return@action
                MaterialAlertDialogBuilder(this).setTitle(R.string.launcher_title)
                    .setMessage(getString(R.string.launcher_confirm, getString(labels[index])))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.launcher_apply) { _, _ ->
                        GuardianGate.action(this) {
                            val result = appearance.select(styles[index]); refresh()
                            group.check(ids[styles.indexOf(appearance.current())])
                            Toast.makeText(this, if (result.isSuccess) R.string.launcher_saved else R.string.launcher_failed, Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        }
    }
}
