package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.audio.VoiceRecorderActivity
import com.nexvary.recorder.databinding.ActivityMainBinding
import com.nexvary.recorder.live.LiveBroadcastActivity
import com.nexvary.recorder.media.AudioReplaceActivity
import com.nexvary.recorder.screen.ScreenRecorderActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnScreen.setOnClickListener { startActivity(Intent(this, ScreenRecorderActivity::class.java)) }
        binding.btnVoice.setOnClickListener { startActivity(Intent(this, VoiceRecorderActivity::class.java)) }
        binding.btnReplace.setOnClickListener { startActivity(Intent(this, AudioReplaceActivity::class.java)) }
        binding.btnLive.setOnClickListener { startActivity(Intent(this, LiveBroadcastActivity::class.java)) }
    }
}
