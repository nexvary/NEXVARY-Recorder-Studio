package com.nexvary.recorder.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.nexvary.recorder.R

object ThemeManager {
    private const val PREFS = "nexvary_ui"
    private const val KEY_THEME = "theme_index"

    private val themes = intArrayOf(
        R.style.Theme_NexvaryRecorder_Blue,
        R.style.Theme_NexvaryRecorder_Emerald,
        R.style.Theme_NexvaryRecorder_Purple,
        R.style.Theme_NexvaryRecorder_Amber
    )

    fun apply(activity: Activity) {
        val index = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, 0)
            .coerceIn(themes.indices)
        activity.setTheme(themes[index])
    }

    fun cycle(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = (prefs.getInt(KEY_THEME, 0) + 1) % themes.size
        prefs.edit().putInt(KEY_THEME, next).apply()
        return next
    }

    @StringRes
    fun currentNameRes(context: Context): Int {
        return when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0)) {
            1 -> R.string.theme_emerald
            2 -> R.string.theme_purple
            3 -> R.string.theme_amber
            else -> R.string.theme_electric_blue
        }
    }
}
