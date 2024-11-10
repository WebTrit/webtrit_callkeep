package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.helpers.PermissionsHelper

class PigeonPermissionsApi(
    private val context: Context,
) : PHostPermissionsApi {
    override fun getFullScreenIntentPermissionStatus(callback: (Result<PSpecialPermissionStatusTypeEnum>) -> Unit) {
        val screenIntentPermissionAvailable = PermissionsHelper(context).canUseFullScreenIntent()
        callback.invoke(Result.success(if (screenIntentPermissionAvailable) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED))
    }

    override fun openFullScreenIntentSettings(callback: (Result<Boolean>) -> Unit) {
        callback.invoke(Result.success(PermissionsHelper(context).launchFullScreenIntentSettings()))
    }
}
