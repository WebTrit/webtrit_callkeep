package com.webtrit.callkeep.common

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
        val keyguardManager: KeyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val inKeyguardRestrictedInputMode: Boolean = keyguardManager.inKeyguardRestrictedInputMode()

        val isLocked = if (inKeyguardRestrictedInputMode) {
            true
        } else {
            !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }
        return isLocked
    }
}
