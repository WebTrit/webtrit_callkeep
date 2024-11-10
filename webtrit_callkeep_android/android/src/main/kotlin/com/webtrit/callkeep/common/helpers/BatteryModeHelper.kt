package com.webtrit.callkeep.common.helpers

import android.content.Context
import android.os.PowerManager
import android.app.ActivityManager
import android.os.Build

class BatteryModeHelper(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Checks if the app is in Unrestricted mode (ignoring battery optimizations).
     */
    fun isUnrestricted(): Boolean {
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Checks if the app is in Restricted mode.
     * This attempts to infer "restricted" status, as Android does not directly expose it.
     */
    fun isRestricted(): Boolean {
        return !isUnrestricted() && isBackgroundRestricted()
    }

    /**
     * Checks if the app is in Optimized mode (not ignoring battery optimizations and not restricted).
     */
    fun isOptimized(): Boolean {
        return !isUnrestricted() && !isRestricted()
    }

    /**
     * Checks if the app has background restrictions imposed (may indicate "restricted" mode).
     */
    private fun isBackgroundRestricted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activityManager.isBackgroundRestricted
        } else {
            return false
        }
    }
}
