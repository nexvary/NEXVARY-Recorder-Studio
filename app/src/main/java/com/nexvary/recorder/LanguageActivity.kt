package com.nexvary.recorder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nexvary.recorder.databinding.ActivityLanguageBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets

class LanguageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSystem.setOnClickListener { applyLanguage("") }
        binding.btnArabic.setOnClickListener { applyLanguage("ar") }
        binding.btnEnglish.setOnClickListener { applyLanguage("en") }
        binding.btnTurkish.setOnClickListener { applyLanguage("tr") }
        binding.btnSpanish.setOnClickListener { applyLanguage("es") }
        binding.btnGerman.setOnClickListener { applyLanguage("de") }
        binding.btnItalian.setOnClickListener { applyLanguage("it") }
        binding.btnFrench.setOnClickListener { applyLanguage("fr") }
        binding.btnUrdu.setOnClickListener { applyLanguage("ur") }
        binding.btnPersian.setOnClickListener { applyLanguage("fa") }
        binding.btnRussian.setOnClickListener { applyLanguage("ru") }
    }

    private fun applyLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
