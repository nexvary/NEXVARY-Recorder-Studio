package com.nexvary.recorder.privacy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Only the launcher entry changes. App identity, notifications and privacy indicators stay visible. */
class LauncherAppearance(private val context: Context) {
    enum class Style(val alias: String) { ORIGINAL("Original"), CLOCK("Clock"), CALENDAR("Calendar") }
    companion object { private val changeLock = Any() }
    private val manager get() = context.packageManager
    fun component(style: Style) = ComponentName(context.packageName, "${context.packageName}.launcher.${style.alias}")
    fun enabled(): List<Style> = Style.entries.filter { style ->
        when (manager.getComponentEnabledSetting(component(style))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style == Style.ORIGINAL
            else -> false
        }
    }
    fun current() = enabled().firstOrNull() ?: Style.ORIGINAL

    fun select(style: Style): Result<Unit> = synchronized(changeLock) { runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(Style.entries.map { candidate ->
                PackageManager.ComponentEnabledSetting(component(candidate), state(candidate == style), PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable the replacement BEFORE disabling anything: interruption cannot leave no entry.
            manager.setComponentEnabledSetting(component(style), state(true), PackageManager.DONT_KILL_APP)
            Style.entries.filter { it != style }.forEach {
                manager.setComponentEnabledSetting(component(it), state(false), PackageManager.DONT_KILL_APP)
            }
        }
        check(enabled() == listOf(style)) { "Launcher change did not persist" }
    }.onFailure {
        // Best-effort recovery always restores an entry before cleaning up other aliases.
        runCatching {
            manager.setComponentEnabledSetting(component(Style.ORIGINAL), state(true), PackageManager.DONT_KILL_APP)
            Style.entries.filter { it != Style.ORIGINAL }.forEach {
                manager.setComponentEnabledSetting(component(it), state(false), PackageManager.DONT_KILL_APP)
            }
        }
    }
    }
    private fun state(enabled: Boolean) = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
