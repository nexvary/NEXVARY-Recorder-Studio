package com.ibmempire.recorder.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.ibmempire.recorder.R

object ThemeManager {
    private const val PREFS = "ibm_empire_ui"
    private const val KEY_THEME = "theme_index"

    private val themes = intArrayOf(
        R.style.Theme_IBMEmpireRecorder_Blue,
        R.style.Theme_IBMEmpireRecorder_Emerald,
        R.style.Theme_IBMEmpireRecorder_Purple,
        R.style.Theme_IBMEmpireRecorder_Amber,
        R.style.Theme_IBMEmpireRecorder_Cyan,
        R.style.Theme_IBMEmpireRecorder_Teal,
        R.style.Theme_IBMEmpireRecorder_Lime,
        R.style.Theme_IBMEmpireRecorder_Rose,
        R.style.Theme_IBMEmpireRecorder_Crimson,
        R.style.Theme_IBMEmpireRecorder_Orange,
        R.style.Theme_IBMEmpireRecorder_Indigo,
        R.style.Theme_IBMEmpireRecorder_Silver
    )

    private val themeNames = intArrayOf(
        R.string.theme_electric_blue,
        R.string.theme_emerald,
        R.string.theme_purple,
        R.string.theme_amber,
        R.string.theme_cyan,
        R.string.theme_teal,
        R.string.theme_lime,
        R.string.theme_rose,
        R.string.theme_crimson,
        R.string.theme_orange,
        R.string.theme_indigo,
        R.string.theme_silver
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

    fun themeNameResIds(): IntArray = themeNames.copyOf()

    @StringRes
    fun currentNameRes(context: Context): Int = themeNames[currentIndex(context)]
}
