package com.ibmempire.recorder.screen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import com.ibmempire.recorder.R
import kotlin.math.abs

class FloatingRecorderOverlay(
    private val context: Context,
    private val onPauseResumeRequested: () -> Unit,
    private val onCameraToggleRequested: () -> Unit,
    private val onTouchesToggleRequested: () -> Unit,
    private val onOpenRequested: () -> Unit,
    private val onStopRequested: () -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var pauseIcon: ImageView? = null
    private var cameraIcon: ImageView? = null
    private var touchesIcon: ImageView? = null
    private var chronometer: Chronometer? = null
    private var cameraEnabled = false
    private var touchesEnabled = false

    fun show(startElapsedRealtime: Long = SystemClock.elapsedRealtime()) {
        if (root != null || !Settings.canDrawOverlays(context)) return

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(Color.argb(242, 5, 14, 24))
                setStroke(dp(1), Color.rgb(56, 189, 248))
            }
            elevation = dp(14).toFloat()
            contentDescription = context.getString(R.string.floating_control_desc)
        }

        fun actionIcon(
            imageRes: Int,
            descriptionRes: Int,
            tint: Int = Color.WHITE
        ) = ImageView(context).apply {
            setImageResource(imageRes)
            setColorFilter(tint)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            contentDescription = context.getString(descriptionRes)
            visibility = View.GONE
        }

        val mainIcon = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            contentDescription = context.getString(R.string.floating_control_desc)
        }

        chronometer = Chronometer(context).apply {
            base = startElapsedRealtime
            format = "%s"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(dp(5), 0, dp(5), 0)
            visibility = View.GONE
            start()
        }

        pauseIcon = actionIcon(
            android.R.drawable.ic_media_pause,
            R.string.pause_recording
        ).apply {
            setOnClickListener { onPauseResumeRequested() }
        }

        cameraIcon = actionIcon(
            android.R.drawable.ic_menu_camera,
            R.string.camera_overlay
        ).apply {
            setOnClickListener { onCameraToggleRequested() }
        }

        touchesIcon = actionIcon(
            android.R.drawable.ic_menu_edit,
            R.string.show_touches
        ).apply {
            setOnClickListener { onTouchesToggleRequested() }
        }

        val openIcon = actionIcon(
            android.R.drawable.ic_menu_view,
            R.string.screen_record_title
        ).apply {
            setOnClickListener { onOpenRequested() }
        }

        val stopIcon = actionIcon(
            android.R.drawable.ic_menu_close_clear_cancel,
            R.string.stop_save,
            Color.rgb(248, 113, 113)
        ).apply {
            setOnClickListener { onStopRequested() }
        }

        container.addView(mainIcon)
        container.addView(chronometer)
        container.addView(pauseIcon)
        container.addView(cameraIcon)
        container.addView(touchesIcon)
        container.addView(openIcon)
        container.addView(stopIcon)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(150)
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        mainIcon.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()

                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        moved = true
                    }

                    layoutParams.x = (startX - dx).coerceAtLeast(0)
                    layoutParams.y = (startY + dy).coerceAtLeast(0)

                    runCatching {
                        windowManager.updateViewLayout(container, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        updateExpandedState()
                        runCatching {
                            windowManager.updateViewLayout(container, layoutParams)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        mainIcon.setOnLongClickListener {
            onStopRequested()
            true
        }

        root = container
        params = layoutParams
        windowManager.addView(container, layoutParams)
        updateFeatureTints()
    }

    private fun updateExpandedState() {
        val visibility = if (expanded) View.VISIBLE else View.GONE
        chronometer?.visibility = visibility
        pauseIcon?.visibility = visibility
        cameraIcon?.visibility = visibility
        touchesIcon?.visibility = visibility

        root?.let { container ->
            if (container.childCount >= 7) {
                container.getChildAt(5).visibility = visibility
                container.getChildAt(6).visibility = visibility
            }
        }
    }

    fun updatePaused(paused: Boolean) {
        pauseIcon?.setImageResource(
            if (paused) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        pauseIcon?.contentDescription = context.getString(
            if (paused) R.string.resume_recording
            else R.string.pause_recording
        )
    }

    fun updateCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
        updateFeatureTints()
    }

    fun updateTouchesEnabled(enabled: Boolean) {
        touchesEnabled = enabled
        updateFeatureTints()
    }

    private fun updateFeatureTints() {
        cameraIcon?.setColorFilter(
            if (cameraEnabled) Color.rgb(52, 211, 153)
            else Color.WHITE
        )
        touchesIcon?.setColorFilter(
            if (touchesEnabled) Color.rgb(52, 211, 153)
            else Color.WHITE
        )
    }

    fun hide() {
        chronometer?.stop()
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        params = null
        pauseIcon = null
        cameraIcon = null
        touchesIcon = null
        chronometer = null
        expanded = false
    }
}
