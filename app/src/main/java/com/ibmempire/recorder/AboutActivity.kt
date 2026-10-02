package com.ibmempire.recorder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ibmempire.recorder.databinding.ActivityAboutBinding
import com.ibmempire.recorder.ui.ThemeManager
import com.ibmempire.recorder.ui.UiInsets

class AboutActivity : AppCompatActivity() {
    companion object {
        private const val PREFS = "ibm_empire_about"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_WEBSITE = "website"
        private const val KEY_FACEBOOK = "facebook"
        private const val KEY_EMAIL = "email"
        private const val KEY_YOUTUBE = "youtube"
        private const val KEY_X = "x"
    }

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSaveAbout.setOnClickListener { saveProfile() }
        binding.btnResetAbout.setOnClickListener { resetProfile() }

        binding.btnWebsite.setOnClickListener { openSavedUrl(binding.editWebsite.text?.toString()) }
        binding.btnFacebook.setOnClickListener { openSavedUrl(binding.editFacebook.text?.toString()) }
        binding.btnEmail.setOnClickListener { openSavedEmail(binding.editEmail.text?.toString()) }
        binding.btnYoutube.setOnClickListener { openSavedUrl(binding.editYoutube.text?.toString()) }
        binding.btnX.setOnClickListener { openSavedUrl(binding.editX.text?.toString()) }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        binding.txtVersion.text = getString(R.string.version_format, versionName)
        loadProfile()
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        binding.editAboutTitle.setText(prefs.getString(KEY_TITLE, getString(R.string.about_default_title)))
        binding.editAboutBody.setText(prefs.getString(KEY_BODY, getString(R.string.about_default_body)))
        binding.editWebsite.setText(prefs.getString(KEY_WEBSITE, ""))
        binding.editFacebook.setText(prefs.getString(KEY_FACEBOOK, ""))
        binding.editEmail.setText(prefs.getString(KEY_EMAIL, ""))
        binding.editYoutube.setText(prefs.getString(KEY_YOUTUBE, ""))
        binding.editX.setText(prefs.getString(KEY_X, ""))
        refreshActions()
    }

    private fun saveProfile() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_TITLE, binding.editAboutTitle.text?.toString()?.trim().orEmpty())
            .putString(KEY_BODY, binding.editAboutBody.text?.toString()?.trim().orEmpty())
            .putString(KEY_WEBSITE, binding.editWebsite.text?.toString()?.trim().orEmpty())
            .putString(KEY_FACEBOOK, binding.editFacebook.text?.toString()?.trim().orEmpty())
            .putString(KEY_EMAIL, binding.editEmail.text?.toString()?.trim().orEmpty())
            .putString(KEY_YOUTUBE, binding.editYoutube.text?.toString()?.trim().orEmpty())
            .putString(KEY_X, binding.editX.text?.toString()?.trim().orEmpty())
            .apply()
        refreshActions()
        Toast.makeText(this, R.string.about_saved, Toast.LENGTH_SHORT).show()
    }

    private fun resetProfile() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
        loadProfile()
        Toast.makeText(this, R.string.about_reset, Toast.LENGTH_SHORT).show()
    }

    private fun refreshActions() {
        binding.btnWebsite.isEnabled = !binding.editWebsite.text.isNullOrBlank()
        binding.btnFacebook.isEnabled = !binding.editFacebook.text.isNullOrBlank()
        binding.btnEmail.isEnabled = !binding.editEmail.text.isNullOrBlank()
        binding.btnYoutube.isEnabled = !binding.editYoutube.text.isNullOrBlank()
        binding.btnX.isEnabled = !binding.editX.text.isNullOrBlank()
    }

    private fun openSavedUrl(raw: String?) {
        val value = raw?.trim().orEmpty()
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
            Toast.makeText(this, R.string.about_invalid_link, Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSavedEmail(raw: String?) {
        val email = raw?.trim().orEmpty()
        if (email.isBlank() || !email.contains("@")) {
            Toast.makeText(this, R.string.about_invalid_email, Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show()
        }
    }
}
