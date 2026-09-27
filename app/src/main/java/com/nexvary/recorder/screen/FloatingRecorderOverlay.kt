package com.nexvary.recorder.screen

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import com.nexvary.recorder.R
import kotlin.math.abs

class FloatingRecorderOverlay(
    private val context: Context,
    private val onStopRequested: () -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun show() {
        if (root != null || !Settings.canDrawOverlays(context)) return

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(235, 7, 17, 29))
                setStroke(dp(1), Color.rgb(56, 189, 248))
            }
            elevation = dp(10).toFloat()
            contentDescription = context.getString(R.string.floating_control_desc)
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_feature_screen)
            setColorFilter(Color.rgb(239, 68, 68))
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            contentDescription = context.getString(R.string.floating_control_desc)
        }

        val label = TextView(context).apply {
            text = "REC"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(dp(4), 0, 0, 0)
        }

        container.addView(icon)
        container.addView(label)

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

        container.setOnTouchListener { _, event ->
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
                    runCatching { windowManager.updateViewLayout(container, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openRecorderControls()
                    true
                }
                else -> false
            }
        }

        container.setOnLongClickListener {
            onStopRequested()
            true
        }

        root = container
        params = layoutParams
        windowManager.addView(container, layoutParams)
    }

    private fun openRecorderControls() {
        context.startActivity(
            Intent(context, ScreenRecorderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        params = null
    }
}
