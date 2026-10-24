package com.nexvary.recorder.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nexvary.npse.ShizukuAccess
import com.nexvary.npse.core.AccessPolicy

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
        val state = ShizukuAccess.state()
        val running = state != AccessPolicy.State.UNAVAILABLE
        val authorized = state == AccessPolicy.State.SHELL
        return Result(installed, running, authorized, Build.VERSION.SDK_INT >= 31)
    }

    fun requestAuthorization(): Boolean {
        return if (ShizukuAccess.state() == AccessPolicy.State.SHELL) true else ShizukuAccess.request(2061)
    }
}
