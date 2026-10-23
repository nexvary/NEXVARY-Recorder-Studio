package com.nexvary.recorder.media

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager

/** App-private local playback. Vault entries keep their separate secure player. */
@androidx.annotation.OptIn(UnstableApi::class)
class InternalPlayerActivity : AppCompatActivity() {
    private lateinit var view: PlayerView
    private lateinit var status: TextView
    private lateinit var name: TextView
    private var media: Uri? = null
    private var displayName = ""
    private var position = 0L
    private var shouldPlay = true
    private var speed = 1f
    private var player: ExoPlayer? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            releasePlayer(); media = uri; displayName = ""; position = 0; shouldPlay = true
            initializePlayer()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val restored = savedInstanceState?.getParcelable<Uri>("media")
        media = (restored ?: intent.data)?.takeIf { it.scheme == "content" }
        displayName = savedInstanceState?.getString("name") ?: intent.getStringExtra("name").orEmpty()
        position = savedInstanceState?.getLong("position", 0) ?: 0
        shouldPlay = savedInstanceState?.getBoolean("play", true) ?: true
        speed = savedInstanceState?.getFloat("speed", 1f) ?: 1f
        val root = StudioPage.install(this, R.string.player_title)
        name = DeliveryUi.text(root, R.string.player_choose).apply { id = R.id.playerName }
        view = PlayerView(this).apply {
            id = R.id.internalPlayer
            setShowNextButton(false); setShowPreviousButton(false)
            setShowRewindButton(true); setShowFastForwardButton(true)
            controllerShowTimeoutMs = 0
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = if (landscape) (resources.configuration.screenHeightDp - 100).coerceAtLeast(180) else 240
        root.addView(view, LinearLayout.LayoutParams(-1, (height * resources.displayMetrics.density).toInt()))
        status = DeliveryUi.text(root, R.string.player_choose).apply { id = R.id.playerStatus; accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        DeliveryUi.button(root, R.string.player_fullscreen, R.id.playerFullscreen) {
            requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val speeds = listOf(.5f, 1f, 1.5f, 2f)
        val speedButton = DeliveryUi.button(root, R.string.player_speed, R.id.playerSpeed) {
            speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
            player?.setPlaybackSpeed(speed)
            findViewById<android.widget.Button>(R.id.playerSpeed).text = getString(R.string.player_speed_value, speed.toString())
        }
        speedButton.text = getString(R.string.player_speed_value, speed.toString())
        DeliveryUi.button(root, R.string.player_choose, R.id.playerChoose) { picker.launch(arrayOf("video/*", "audio/*")) }
        DeliveryUi.text(root, R.string.player_local_note)
    }
    override fun onStart() { super.onStart(); initializePlayer() }
    override fun onResume() { super.onResume(); player?.playWhenReady = shouldPlay }
    override fun onPause() { player?.let { shouldPlay = it.playWhenReady; it.pause() }; super.onPause() }
    override fun onStop() { releasePlayer(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        player?.let { position = it.currentPosition; if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) shouldPlay = it.playWhenReady }
        outState.putParcelable("media", media); outState.putString("name", displayName)
        outState.putLong("position", position); outState.putBoolean("play", shouldPlay)
        outState.putFloat("speed", speed); super.onSaveInstanceState(outState)
    }
    private fun initializePlayer() {
        val uri = media ?: return
        if (player != null) return
        name.text = displayName.ifBlank { getString(R.string.player_title) }
        status.setText(R.string.player_loading)
        val current = ExoPlayer.Builder(this).build()
        player = current; view.player = current
        current.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        current.setHandleAudioBecomingNoisy(true)
        current.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== current) return
                status.setText(if (current.playerError != null) R.string.player_error else when (playbackState) {
                    Player.STATE_READY -> R.string.player_ready
                    Player.STATE_ENDED -> R.string.player_finished
                    else -> R.string.player_loading
                })
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (player === current) view.keepScreenOn = isPlaying }
            override fun onPlayerError(error: PlaybackException) { if (player === current) { status.setText(R.string.player_error); view.keepScreenOn = false } }
        })
        current.setMediaItem(MediaItem.fromUri(uri)); current.seekTo(position)
        current.setPlaybackSpeed(speed); current.prepare(); current.playWhenReady = shouldPlay
    }
    private fun releasePlayer() {
        val previous = player; player = null
        previous?.let { position = it.currentPosition; view.player = null; it.release() }
        if (::view.isInitialized) view.keepScreenOn = false
    }
    companion object {
        fun intent(context: Context, uri: Uri, name: String) = Intent(context, InternalPlayerActivity::class.java).setData(uri).putExtra("name", name)
    }
}
