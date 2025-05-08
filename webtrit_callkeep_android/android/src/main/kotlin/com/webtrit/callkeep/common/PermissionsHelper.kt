package com.webtrit.callkeep.common

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import io.flutter.Log

class PermissionsHelper(private val context: Context) {
    fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            Log.i(TAG, "Can't check full screen intent permission on this device")
            true
        }
    }

    fun launchFullScreenIntentSettings() {
        val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun launchSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    fun hasCameraPermission(): Boolean {
        val cameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA)
        return cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if notification permission is granted on Android 13+ (API level 33).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        private const val TAG = "PermissionsHelper"
    }
}
