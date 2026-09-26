package com.nexvary.recorder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nexvary.recorder.databinding.ActivityAboutBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets

class AboutActivity : AppCompatActivity() {
    companion object {
        const val WEBSITE = "https://nexvary.com/"
        const val FACEBOOK = "https://www.facebook.com/share/14p9krEn5ij/"
        const val EMAIL = "info@nexvary.com"
        const val YOUTUBE = "https://www.youtube.com/@NexvaryInc"
        const val X = "https://x.com/Nexvary"
    }

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnWebsite.setOnClickListener { openUri(WEBSITE) }
        binding.btnFacebook.setOnClickListener { openUri(FACEBOOK) }
        binding.btnEmail.setOnClickListener { openEmail() }
        binding.btnYoutube.setOnClickListener { openUri(YOUTUBE) }
        binding.btnX.setOnClickListener { openUri(X) }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        binding.txtVersion.text = getString(R.string.version_format, versionName)
    }

    private fun openUri(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openEmail() {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show()
        }
    }
}
