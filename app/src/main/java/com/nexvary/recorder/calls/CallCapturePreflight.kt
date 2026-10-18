package com.nexvary.recorder.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

/** Shizuku status only; capture permission and call audio access are separate. */
object CallCapturePreflight {
    data class Result(
        val shizukuInstalled: Boolean,
        val shizukuRunning: Boolean,
        val shizukuAuthorized: Boolean,
        val supportedAndroidVersion: Boolean
    ) {
        val readyForIntegration: Boolean
            get() = shizukuInstalled && shizukuRunning && shizukuAuthorized && supportedAndroidVersion
    }

    fun inspect(context: Context): Result {
        val installed = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return Result(installed, running, authorized, Build.VERSION.SDK_INT >= 31)
    }

    fun requestAuthorization(): Boolean {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return false
        if (runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) return true
        return runCatching {
            Shizuku.requestPermission(2061)
            true
        }.getOrDefault(false)
    }
}
