package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.safety.QuickRescueActivity
import com.nexvary.recorder.safety.SafetyCaptureActivity
import com.nexvary.recorder.calls.CallRecorderActivity
import com.nexvary.recorder.camera.BackgroundCameraActivity
import com.nexvary.recorder.audio.VoiceRecorderActivity
import com.nexvary.recorder.databinding.ActivityMainBinding
import com.nexvary.recorder.live.LiveBroadcastActivity
import com.nexvary.recorder.media.MediaToolsActivity
import com.nexvary.recorder.screen.ScreenRecorderActivity
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets
import com.nexvary.recorder.media.RecordingsLibraryActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var appliedTheme = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemeManager.currentIndex(this)
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)

        if (com.nexvary.recorder.guardian.ChildMode.enabled(this)) {
            startActivity(Intent(this, com.nexvary.recorder.guardian.ChildHomeActivity::class.java)); finish(); return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.applyWithBottomBar(binding.root, binding.bottomNav)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        binding.txtVersion.text = getString(R.string.version_format, versionName)
        updateThemeLabel()
        com.nexvary.recorder.ui.FeatureCards.add(binding.setupEntry, R.drawable.asset_family, R.string.setup_title) {
            startActivity(Intent(this, com.nexvary.recorder.family.GuidedSetupActivity::class.java))
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { com.nexvary.recorder.delivery.MailWorker.reconcile(this@MainActivity) };runCatching { com.nexvary.recorder.family.SafetyCheckManager(this@MainActivity).evaluate(true) } }
        }

        binding.btnThemePicker.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            com.nexvary.recorder.family.GuardianGate.action(this) { showThemePicker() }
        }

        binding.cardScreen.setOnClickListener { openScreenRecorder() }
        binding.cardFamily.setOnClickListener { openFamilySafety() }
        binding.cardReplace.setOnClickListener { openMediaTools() }
        binding.cardLive.setOnClickListener { openLiveBroadcast() }
        binding.cardCamera.setOnClickListener { startActivity(Intent(this, BackgroundCameraActivity::class.java)) }
        binding.cardRescue.setOnClickListener { startActivity(Intent(this, com.nexvary.recorder.guardian.GuardianEmergencyActivity::class.java)) }
        binding.cardLibrary.setOnClickListener { startActivity(Intent(this, RecordingsLibraryActivity::class.java)) }
        binding.cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.cardAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        binding.bottomNav.itemIconTintList = null
        // Labels are always visible. Suppress duplicate long-press tooltips so a
        // slow/held tap can still activate a tab rather than consuming its click.
        for (id in listOf(R.id.navHome, R.id.navScreen, R.id.navSafety, R.id.navLive, R.id.navMore)) {
            binding.bottomNav.findViewById<android.view.View>(id)?.let { tab ->
                androidx.appcompat.widget.TooltipCompat.setTooltipText(tab, null)
                tab.isLongClickable = false
            }
        }

        binding.bottomNav.selectedItemId = R.id.navHome
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navHome -> true
                R.id.navScreen -> {
                    openScreenRecorder()
                    false
                }
                R.id.navSafety -> {
                    openFamilySafety()
                    false
                }
                R.id.navLive -> {
                    openLiveBroadcast()
                    false
                }
                R.id.navMore -> {
                    startActivity(Intent(this, MoreActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemeManager.currentIndex(this)) { recreate(); return }
        if (::binding.isInitialized) {
            binding.bottomNav.menu.findItem(R.id.navHome)?.isChecked = true
            updateThemeLabel()
        }
    }

    private fun openScreenRecorder() {
        startActivity(Intent(this, ScreenRecorderActivity::class.java))
    }

    private fun openFamilySafety() {
        startActivity(Intent(this, com.nexvary.recorder.family.FamilyHubActivity::class.java))
    }

    private fun openVoiceStudio() {
        startActivity(Intent(this, VoiceRecorderActivity::class.java))
    }

    private fun openMediaTools() {
        startActivity(Intent(this, com.nexvary.recorder.media.MediaCenterActivity::class.java))
    }

    private fun openLiveBroadcast() {
        startActivity(Intent(this, LiveBroadcastActivity::class.java))
    }

    private fun showThemePicker() {
        val labels = ThemeManager.themeNameResIds()
            .map { getString(it) }
            .toTypedArray()
        val current = ThemeManager.currentIndex(this)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) {
                    ThemeManager.select(this, which)
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateThemeLabel() {
        binding.txtThemeName.text = getString(
            R.string.theme_current_format,
            getString(ThemeManager.currentNameRes(this))
        )
    }
}
