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

    private val themeNames = intArrayOf(
        R.string.theme_electric_blue,
        R.string.theme_emerald,
        R.string.theme_purple,
        R.string.theme_amber
    )

    fun apply(activity: Activity) {
        activity.setTheme(themes[currentIndex(activity)])
    }

    fun currentIndex(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, 0)
            .coerceIn(themes.indices)
    }

    fun select(context: Context, index: Int) {
        val safeIndex = index.coerceIn(themes.indices)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, safeIndex)
            .apply()
    }

    fun cycle(context: Context): Int {
        val next = (currentIndex(context) + 1) % themes.size
        select(context, next)
        return next
    }

    fun themeNameResIds(): IntArray = themeNames.copyOf()

    @StringRes
    fun currentNameRes(context: Context): Int = themeNames[currentIndex(context)]
}
