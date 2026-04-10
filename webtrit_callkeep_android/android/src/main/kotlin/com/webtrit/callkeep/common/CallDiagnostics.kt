package com.webtrit.callkeep.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.services.core.CallkeepCore
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.foreground.ForegroundService
import java.util.Locale

/**
 * A utility object for gathering diagnostic information related to the calling functionality of the application.
 * It collects data about the device, service states, permissions, telecom settings, power management,
 * vendor-specific configurations, and notification channels.
 */
object CallDiagnostics {
    @SuppressLint("BatteryLife")
    fun gatherMap(context: Context): Map<String, Any?> =
        buildMap {
            putAll(getDeviceInfo())
            putAll(getServiceStates(context))
            put("permissions", getPermissionsState(context))
            putAll(getTelecomInfo(context))
            putAll(getPowerManagementInfo(context))
            putAll(getVendorSpecifics(context))
            putAll(getNotificationChannelInfo(context))
            put("lastOutgoingCalls", getFailedCallsLog())
        }

    private fun getDeviceInfo() =
        mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "androidSdk" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
        )

    private fun getServiceStates(context: Context) =
        mapOf(
            "isForegroundServiceRunning" to ForegroundService.isRunning,
            // PhoneConnectionService.isRunning is a JVM-static companion field — it is always
            // false when read from a different OS process (e.g. the main process). Query the OS
            // via ActivityManager to correctly detect it regardless of process boundaries.
            "isPhoneConnectionServiceRunning" to isServiceRunning(context, PhoneConnectionService::class.java),
            "isLockScreen" to Platform.isLockScreen(context),
            "trackerState" to
                runCatching {
                    CallkeepCore.instance.getAll().toString()
                }.getOrElse { "Error: ${it.message}" },
        )

    /**
     * Returns true if the given service class is currently running in any process of this app.
     * Uses [ActivityManager] because companion-object `isRunning` flags are JVM-process-local
     * and cannot be read across Android process boundaries.
     *
     * Both class name and package name are matched to avoid false positives from other apps
     * that might expose a service with the same class name.
     */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name &&
                it.service.packageName == context.packageName
        }
    }

    private fun getPermissionsState(context: Context): Map<String, Boolean> {
        val permsToCheck =
            mutableListOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permsToCheck.add(Manifest.permission.READ_PHONE_NUMBERS)
            permsToCheck.add(Manifest.permission.MANAGE_OWN_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToCheck.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permsToCheck.associateWith { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getTelecomInfo(context: Context): Map<String, Any?> {
        val info =
            mutableMapOf<String, Any?>(
                "isPhoneAccountRegistered" to false,
                "isPhoneAccountEnabled" to null,
                "supportedUriSchemes" to null,
                "defaultDialerPackage" to null,
                "telecomErrorMessage" to null,
            )

        try {
            val telephonyUtils = TelephonyUtils(context)
            val tm = telephonyUtils.getTelecomManager()
            val handle = telephonyUtils.getPhoneAccountHandle()
            val account = tm.getPhoneAccount(handle)

            if (account != null) {
                info["isPhoneAccountRegistered"] = true
                info["isPhoneAccountEnabled"] = account.isEnabled
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info["supportedUriSchemes"] = account.supportedUriSchemes.joinToString(", ")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info["defaultDialerPackage"] = tm.defaultDialerPackage
            }
        } catch (e: Exception) {
            info["telecomErrorMessage"] = "${e::class.simpleName}: ${e.message}"
        }
        return info
    }

    private fun getPowerManagementInfo(context: Context): Map<String, Any?> =
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringOptimizations =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }
            mapOf(
                "isIgnoringBatteryOptimizations" to isIgnoringOptimizations,
                "isPowerSaveMode" to pm.isPowerSaveMode,
                "isInteractive" to pm.isInteractive,
            )
        } catch (e: Exception) {
            mapOf("powerManagementError" to e.message)
        }

    private fun getVendorSpecifics(context: Context): Map<String, Any?> {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val map = mutableMapOf<String, Any?>()

        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains(
                "poco",
            )
        ) {
            map["isMiui"] = !getSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()
            map["miuiVersion"] = getSystemProperty("ro.miui.ui.version.name")

            val autoStartIntent =
                Intent().apply {
                    component =
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        )
                }
            map["hasAutoStartSetting"] = canResolveIntent(context, autoStartIntent)
        }

        if (manufacturer.contains("samsung")) {
            val deviceCareIntent = Intent("com.samsung.android.sm.ACTION_BATTERY")
            map["hasSamsungDeviceCare"] = canResolveIntent(context, deviceCareIntent)
        }

        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            map["emuiVersion"] = getSystemProperty("ro.build.version.emui")
            val appLaunchIntent =
                Intent().apply {
                    component =
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity",
                        )
                }
            map["hasHuaweiAppLaunchSetting"] = canResolveIntent(context, appLaunchIntent)
        }

        return map
    }

    private fun getNotificationChannelInfo(context: Context): Map<String, Any?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyMap()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID

        val channel =
            nm.getNotificationChannel(channelId)
                ?: return mapOf("incomingCallChannelStatus" to "NOT_FOUND")

        return mapOf(
            "incomingCallChannelImportance" to channel.importance,
            "incomingCallChannelSound" to channel.sound?.toString(),
            "incomingCallChannelVibration" to channel.shouldVibrate(),
            "areNotificationsEnabled" to nm.areNotificationsEnabled(),
        )
    }

    private fun getFailedCallsLog(): List<Map<String, Any?>> =
        ForegroundService.failedCallsStore.getAll().map { info ->
            mapOf(
                "callId" to info.callId,
                "source" to info.source.name,
                "reason" to info.reason,
                "timestamp" to info.timestamp,
            )
        }

    private fun canResolveIntent(
        context: Context,
        intent: Intent,
    ): Boolean =
        context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        ) != null

    private fun getSystemProperty(key: String): String? =
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val value = getMethod.invoke(null, key) as String
            value.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
}
