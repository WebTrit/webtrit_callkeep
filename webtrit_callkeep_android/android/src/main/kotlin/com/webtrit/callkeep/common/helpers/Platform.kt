package com.webtrit.callkeep.common.helpers

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager

object Platform {
    fun getLaunchActivity(context: Context): Intent? {
        val pm: PackageManager = context.packageManager
        return pm.getLaunchIntentForPackage(context.packageName)
    }

    fun isLockScreen(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = if (keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked) {
            true
        } else {
            !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }
        return isLocked
    }

    fun isApplicationForeground(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?

        if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
            return false
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager? ?: return false

        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                return true
            }
        }

        return false
    }
}
