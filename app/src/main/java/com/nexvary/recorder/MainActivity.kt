package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.audio.VoiceRecorderActivity
import com.nexvary.recorder.databinding.ActivityMainBinding
import com.nexvary.recorder.live.LiveBroadcastActivity
import com.nexvary.recorder.media.MediaToolsActivity
import com.nexvary.recorder.screen.ScreenRecorderActivity
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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
            getString(R.string.choose_theme)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nav_more)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> openMediaTools()
                    1 -> startActivity(Intent(this, LanguageActivity::class.java))
                    2 -> startActivity(Intent(this, AboutActivity::class.java))
                    3 -> showThemePicker()
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
