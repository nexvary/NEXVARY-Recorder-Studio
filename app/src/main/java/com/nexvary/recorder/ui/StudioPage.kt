package com.nexvary.recorder.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import com.nexvary.recorder.R

/** Shared scroll-safe shell for pages built in code rather than Material XML inflation. */
object StudioPage {
    fun install(activity: Activity, title: Int): LinearLayout {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        content.addView(Button(activity).apply {
            id = R.id.btnBack
            setText(R.string.back)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.START }
            setOnClickListener { activity.finish() }
        })
        content.addView(TextView(activity).apply {
            setText(title)
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, pad)
        })
        val scroll = ScrollView(activity).apply {
            setBackgroundResource(R.drawable.bg_piano_gloss)
            addView(content)
        }
        activity.setContentView(scroll)
        UiInsets.apply(scroll)
        WindowCompat.getInsetsController(activity.window, scroll).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // Native Button/TextView defaults can use the system's light palette. Also
        // style asynchronously inserted library rows, without changing their actions.
        scroll.viewTreeObserver.addOnGlobalLayoutListener { style(content) }
        return content
    }

    private fun style(view: View) {
        if (view.getTag(R.id.studioStyled) != true) {
            view.setTag(R.id.studioStyled, true)
            when (view) {
                is SwitchCompat -> {
                    view.setTextColor(0xFFF5F7FA.toInt())
                    view.thumbTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(0xFF38BDF8.toInt(), 0xFFCBD5E1.toInt())
                    )
                    view.trackTintList = ColorStateList.valueOf(0xFF506171.toInt())
                }
                is CompoundButton -> {
                    view.setTextColor(0xFFF5F7FA.toInt())
                    view.buttonTintList = ColorStateList.valueOf(0xFF38BDF8.toInt())
                }
                is Button -> {
                    view.setTextColor(0xFF080A0C.toInt())
                    view.backgroundTintList = ColorStateList.valueOf(0xFFCCD6E4.toInt())
                    view.minHeight = (48 * view.resources.displayMetrics.density).toInt()
                    view.isAllCaps = false
                }
                is TextView -> view.setTextColor(0xFFF5F7FA.toInt())
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
    }
}
