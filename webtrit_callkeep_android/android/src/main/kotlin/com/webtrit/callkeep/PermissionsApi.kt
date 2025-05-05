package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.BatteryModeHelper
import com.webtrit.callkeep.common.PermissionsHelper

class PermissionsApi(
    private val context: Context,
) : PHostPermissionsApi {
    override fun getFullScreenIntentPermissionStatus(callback: (Result<PSpecialPermissionStatusTypeEnum>) -> Unit) {
        val screenIntentPermissionAvailable = PermissionsHelper(context).canUseFullScreenIntent()
        val status =
            if (screenIntentPermissionAvailable) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED
        callback.invoke(Result.success(status))
    }

    /**
     * Attempts to open the system settings screen for managing the "Use full screen intent" permission.
     *
     * This setting allows the app to show incoming call UI in full screen when the device is locked.
     * The method internally checks and starts the appropriate system intent.
     *
     * @param callback A callback that receives a [Result]:
     * - [Result.success(Unit)] if the settings screen was successfully opened.
     * - [Result.failure] with an exception (e.g., [ActivityNotFoundException]) if the intent cannot be handled.
     *
     * Note: This functionality is only supported on Android 13 (API 33) and above,
     * and may not be available on all devices even on supported versions.
     */
    override fun openFullScreenIntentSettings(callback: (Result<Unit>) -> Unit) {
        try {
            PermissionsHelper(context).launchFullScreenIntentSettings()
            callback.invoke(Result.success(Unit))
        } catch (e: Exception) {
            callback.invoke(Result.failure(e))
        }
    }

    override fun getBatteryMode(callback: (Result<PCallkeepAndroidBatteryMode>) -> Unit) {
        val batteryMode = BatteryModeHelper(context)
        val mode = when {
            batteryMode.isUnrestricted() -> PCallkeepAndroidBatteryMode.UNRESTRICTED
            batteryMode.isRestricted() -> PCallkeepAndroidBatteryMode.RESTRICTED
            batteryMode.isOptimized() -> PCallkeepAndroidBatteryMode.OPTIMIZED
            else -> PCallkeepAndroidBatteryMode.UNKNOWN
        }

        callback.invoke(Result.success(mode))
    }
}
