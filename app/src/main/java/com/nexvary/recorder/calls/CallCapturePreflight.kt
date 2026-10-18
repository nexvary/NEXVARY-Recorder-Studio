package com.nexvary.recorder.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Self-contained preflight checks. No upstream GPL code is copied.
 * A successful check does not imply that capturing both sides of a call works.
 */
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

        // Avoid hard dependency on Shizuku until its API is added to Gradle.
        // Reflection is only a status probe; it does not request privileges.
        val running = runCatching {
            val klass = Class.forName("rikka.shizuku.Shizuku")
            klass.getMethod("pingBinder").invoke(null) == true
        }.getOrDefault(false)
        val authorized = if (!running) false else runCatching {
            val klass = Class.forName("rikka.shizuku.Shizuku")
            klass.getMethod("checkSelfPermission").invoke(null) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return Result(installed, running, authorized, Build.VERSION.SDK_INT >= 31)
    }
}
