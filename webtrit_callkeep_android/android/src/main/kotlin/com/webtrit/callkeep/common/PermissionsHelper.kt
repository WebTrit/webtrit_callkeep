package com.webtrit.callkeep.common

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.Log

class PermissionsHelper(
    private val context: Context,
) {
    fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val granted = notificationManager.canUseFullScreenIntent()
            if (!granted) {
                Log.w(TAG, "USE_FULL_SCREEN_INTENT permission is not granted (Android 14+); full-screen incoming call UI will not appear on lock screen")
            }
            granted
        } else {
            Log.d(TAG, "USE_FULL_SCREEN_INTENT permission check skipped (Android < 14); assuming granted")
            true
        }

    fun launchFullScreenIntentSettings() {
        val intent =
            Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }

    fun launchSettings() {
        val intent =
            Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }

    /**
     * Whether this device is a Xiaomi-family build (MIUI/HyperOS), where the
     * "display pop-up windows while running in background" capability gates
     * showing an Activity over the lock screen.
     */
    fun isXiaomiFamily(): Boolean {
        val brand = (Build.MANUFACTURER ?: "").lowercase()
        return brand == "xiaomi" || brand == "redmi" || brand == "poco"
    }

    /**
     * Best-effort check of the MIUI/HyperOS "display pop-up windows while running
     * in background" capability (OP_BACKGROUND_START_ACTIVITY). There is no public
     * API for it, so this reads the hidden AppOps op via reflection.
     *
     * - Non Xiaomi-family devices: treated as granted (capability does not apply).
     * - Xiaomi-family where the op cannot be read: treated as denied, so the app
     *   can surface guidance rather than silently failing on the lock screen.
     */
    fun isBackgroundActivityStartGranted(): Boolean {
        if (!isXiaomiFamily()) return true
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val method =
                android.app.AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            val mode =
                method.invoke(appOps, OP_BACKGROUND_START_ACTIVITY, context.applicationInfo.uid, context.packageName) as Int
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to read background-activity-start AppOp; treating as denied: ${e.message}")
            false
        }
    }

    /**
     * Opens the MIUI/HyperOS "Other permissions" editor where the
     * "display pop-up windows while running in background" toggle lives.
     * Falls back to app details settings, then to the common settings.
     */
    fun launchBackgroundActivityStartSettings() {
        val pkg = context.packageName
        val candidates =
            listOf(
                Intent()
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", pkg),
                Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", pkg, null),
                ),
            )
        for (intent in candidates) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Background-activity-start settings intent not available: ${e.message}")
            }
        }
        launchSettings()
    }

    fun hasCameraPermission(): Boolean {
        val cameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA)
        return cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    fun hasMicrophonePermission(): Boolean {
        val microphonePermission =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        return microphonePermission == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if notification permission is granted on Android 13+ (API level 33).
     */
    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    companion object {
        private const val TAG = "PermissionsHelper"

        // MIUI/HyperOS AppOps op for "display pop-up windows while running in background".
        private const val OP_BACKGROUND_START_ACTIVITY = 10021
    }
}
