package com.webtrit.callkeep.common

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Platform {
    fun getLaunchActivity(context: Context): Intent? {
        val pm: PackageManager = context.packageManager
        return pm.getLaunchIntentForPackage(context.packageName)
    }

    fun isLockScreen(context: Context): Boolean {
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false

        // isKeyguardLocked() returns true if the lock screen
        // (keyguard) is currently active.
        // This works for both "swipe" and PIN.
        return keyguardManager.isKeyguardLocked
    }
}
