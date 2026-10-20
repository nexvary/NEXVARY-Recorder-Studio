package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.applyWithBottomBar(binding.root, binding.bottomNav)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        binding.txtVersion.text = getString(R.string.version_format, versionName)
        updateThemeLabel()

        binding.btnThemePicker.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showThemePicker()
        }

        binding.cardScreen.setOnClickListener { openScreenRecorder() }
        binding.cardVoice.setOnClickListener { openVoiceStudio() }
        binding.cardReplace.setOnClickListener { openMediaTools() }
        binding.cardLive.setOnClickListener { openLiveBroadcast() }
        binding.cardCamera.setOnClickListener { startActivity(Intent(this, BackgroundCameraActivity::class.java)) }
        binding.cardRescue.setOnClickListener { startActivity(Intent(this, QuickRescueActivity::class.java)) }
        binding.cardLibrary.setOnClickListener { startActivity(Intent(this, RecordingsLibraryActivity::class.java)) }
        binding.cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.cardAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        binding.bottomNav.itemIconTintList = null
        // Labels are always visible. Suppress duplicate long-press tooltips so a
        // slow/held tap can still activate a tab rather than consuming its click.
        for (id in listOf(R.id.navHome, R.id.navScreen, R.id.navVoice, R.id.navLive, R.id.navMore)) {
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
                R.id.navVoice -> {
                    openVoiceStudio()
                    false
                }
                R.id.navLive -> {
                    openLiveBroadcast()
                    false
                }
                R.id.navMore -> {
                    showMoreMenu()
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

    private fun openVoiceStudio() {
        startActivity(Intent(this, VoiceRecorderActivity::class.java))
    }

    private fun openMediaTools() {
        startActivity(Intent(this, MediaToolsActivity::class.java))
    }

    private fun openLiveBroadcast() {
        startActivity(Intent(this, LiveBroadcastActivity::class.java))
    }

    private fun showMoreMenu() {
        val labels = arrayOf(
            getString(R.string.media_tools_title),
            getString(R.string.language_title),
            getString(R.string.about_title),
            getString(R.string.choose_theme),
            getString(R.string.camera_title),
            getString(R.string.call_title),
            getString(R.string.safety_title),
            getString(R.string.rescue_title)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nav_more)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> openMediaTools()
                    1 -> startActivity(Intent(this, LanguageActivity::class.java))
                    2 -> startActivity(Intent(this, AboutActivity::class.java))
                    3 -> showThemePicker()
                    4 -> startActivity(Intent(this, BackgroundCameraActivity::class.java))
                    5 -> startActivity(Intent(this, CallRecorderActivity::class.java))
                    6 -> startActivity(Intent(this, SafetyCaptureActivity::class.java))
                    7 -> startActivity(Intent(this, QuickRescueActivity::class.java))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
