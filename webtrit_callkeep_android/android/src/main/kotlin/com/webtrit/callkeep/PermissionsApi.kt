package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.helpers.BatteryModeHelper
import com.webtrit.callkeep.common.helpers.PermissionsHelper

class PermissionsApi(
    private val context: Context,
) : PHostPermissionsApi {
    override fun getFullScreenIntentPermissionStatus(callback: (Result<PSpecialPermissionStatusTypeEnum>) -> Unit) {
        val screenIntentPermissionAvailable = PermissionsHelper(context).canUseFullScreenIntent()
        val status =
            if (screenIntentPermissionAvailable) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED
        callback.invoke(Result.success(status))
    }

    override fun openFullScreenIntentSettings(callback: (Result<Boolean>) -> Unit) {
        callback.invoke(Result.success(PermissionsHelper(context).launchFullScreenIntentSettings()))
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
