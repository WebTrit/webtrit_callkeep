package com.webtrit.callkeep.common.helpers

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.Log

class PermissionsHelper(private val context: Context) {
    fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canUseFullScreenIntent = notificationManager.canUseFullScreenIntent()
            canUseFullScreenIntent
        } else {
            Log.i(TAG, "Can't check full screen intent permission on this device")
            true
        }
    }

    fun launchFullScreenIntentSettings(): Boolean {
        return try {
            val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.i(TAG, "Error launching full screen intent settings", e)
            false
        }
    }

    fun hasCameraPermission(): Boolean {
        val cameraPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA)
        return cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "PermissionsHelper"
    }
}
