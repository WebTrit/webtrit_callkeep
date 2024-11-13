package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.common.ActivityHolder

class PigeonIsolateApi(
    private val context: Context
) : PHostIsolateApi {

    override fun setUpCallback(
        callbackDispatcher: Long,
        onStartHandler: Long,
        onChangedLifecycleHandler: Long,
        callback: (Result<Unit>) -> Unit
    ) {
    }

    override fun setUp(
        type: PCallkeepIncomingType,
        autoRestartOnTerminate: Boolean,
        autoStartOnBoot: Boolean,
        androidNotificationName: String?,
        androidNotificationDescription: String?,
        callback: (Result<Unit>) -> Unit
    ) {
    }

    override fun startService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "startService")
    }

    override fun stopService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "stopService")
    }

    override fun finishActivity(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "finishActivity")
        try {
            ActivityHolder.finish()

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    companion object {
        const val TAG = "PigeonServiceApi"
    }
}
