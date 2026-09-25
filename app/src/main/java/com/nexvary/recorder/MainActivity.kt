package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.audio.VoiceRecorderActivity
import com.nexvary.recorder.databinding.ActivityMainBinding
import com.nexvary.recorder.live.LiveBroadcastActivity
import com.nexvary.recorder.media.AudioReplaceActivity
import com.nexvary.recorder.screen.ScreenRecorderActivity
import com.nexvary.recorder.ui.ThemeManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtThemeName.text = "الثيم الحالي: ${ThemeManager.currentName(this)}"

        binding.themeHeader.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            ThemeManager.cycle(this)
            recreate()
        }

        binding.cardScreen.setOnClickListener {
            startActivity(Intent(this, ScreenRecorderActivity::class.java))
        }
        binding.cardVoice.setOnClickListener {
            startActivity(Intent(this, VoiceRecorderActivity::class.java))
        }
        binding.cardReplace.setOnClickListener {
            startActivity(Intent(this, AudioReplaceActivity::class.java))
        }
        binding.cardLive.setOnClickListener {
            startActivity(Intent(this, LiveBroadcastActivity::class.java))
        }
    }
}
