package com.webtrit.callkeep

import PermissionsHelper
import android.content.Context

class PigeonPermissionsApi(
    private val context: Context,
) : PHostPermissionsApi {
    override fun getFullScreenIntentPermissionStatus(callback: (Result<PSpecialPermissionStatusTypeEnum>) -> Unit) {
        val screenIntentPermissionAvailable = PermissionsHelper(context).checkFullScreenIntentPermission()
        callback.invoke(Result.success(if (screenIntentPermissionAvailable) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED))
    }

    override fun openFullScreenIntentSettings(callback: (Result<Boolean>) -> Unit) {
        callback.invoke(Result.success(PermissionsHelper(context).launchFullScreenIntentSettings()))
    }
}
