package com.nexvary.recorder.screen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexvary.recorder.R
import kotlin.math.abs

class FloatingRecorderOverlay(
    private val context: Context,
    private val onPauseResumeRequested: () -> Unit,
    private val onStopRequested: () -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    fun show() {
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
                setColor(Color.argb(238, 7, 17, 29))
                setStroke(dp(1), Color.rgb(56, 189, 248))
            }
            elevation = dp(12).toFloat()
            contentDescription = context.getString(R.string.floating_control_desc)
        }

        fun actionIcon(
            imageRes: Int,
            descriptionRes: Int,
            tint: Int
        ) = ImageView(context).apply {
            setImageResource(imageRes)
            setColorFilter(tint)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            contentDescription = context.getString(descriptionRes)
        }

        val mainIcon = actionIcon(
            R.drawable.ic_feature_screen,
            R.string.floating_control_desc,
            Color.rgb(239, 68, 68)
        )

        val pauseIcon = actionIcon(
            android.R.drawable.ic_media_pause,
            R.string.pause_recording,
            Color.WHITE
        ).apply {
            visibility = View.GONE
            setOnClickListener { onPauseResumeRequested() }
        }

        val stopIcon = actionIcon(
            android.R.drawable.ic_menu_close_clear_cancel,
            R.string.stop_save,
            Color.rgb(239, 68, 68)
        ).apply {
            visibility = View.GONE
            setOnClickListener { onStopRequested() }
        }

        container.addView(mainIcon)
        container.addView(pauseIcon)
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
            x = dp(12)
            y = dp(160)
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
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true

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
                        pauseIcon.visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        stopIcon.visibility =
                            if (expanded) View.VISIBLE else View.GONE
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
    }

    fun updatePaused(paused: Boolean) {
        val container = root ?: return
        if (container.childCount < 2) return

        val pauseIcon = container.getChildAt(1) as? ImageView ?: return
        pauseIcon.setImageResource(
            if (paused) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        pauseIcon.contentDescription = context.getString(
            if (paused) R.string.resume_recording
            else R.string.pause_recording
        )
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        params = null
        expanded = false
    }
}
