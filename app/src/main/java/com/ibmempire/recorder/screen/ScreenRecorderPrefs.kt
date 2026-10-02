package com.ibmempire.recorder.screen

import android.content.Context

object ScreenRecorderPrefs {
    private const val PREFS = "screen_recorder"
    private const val KEY_TREE_URI = "output_tree_uri"
    private const val KEY_TREE_NAME = "output_tree_name"
    private const val KEY_COUNTDOWN = "countdown_seconds"
    private const val KEY_FLOATING = "floating_control"
    private const val KEY_TOUCHES = "show_touches"

    fun outputTreeUri(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, "")
            .orEmpty()

    fun outputTreeName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_NAME, "")
            .orEmpty()

    fun setOutputTree(context: Context, uri: String, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, uri)
            .putString(KEY_TREE_NAME, name)
            .apply()
    }

    fun clearOutputTree(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_TREE_NAME)
            .apply()
    }

    fun countdownSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_COUNTDOWN, 3)

    fun setCountdownSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COUNTDOWN, seconds.coerceIn(0, 10))
            .apply()
    }

    fun floatingControl(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLOATING, true)

    fun setFloatingControl(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FLOATING, enabled)
            .apply()
    }

    fun showTouches(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOUCHES, false)

    fun setShowTouches(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOUCHES, enabled)
            .apply()
    }
}
