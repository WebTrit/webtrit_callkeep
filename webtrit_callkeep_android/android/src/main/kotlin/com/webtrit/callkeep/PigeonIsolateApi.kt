package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.SignalingService

class PigeonIsolateApi(
    private val context: Context
) : PHostIsolateApi {

    override fun initializeSignalingServiceCallback(
        callbackDispatcher: Long,
        onStartHandler: Long,
        onChangedLifecycleHandler: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.setCallbackDispatcher(context, callbackDispatcher)
        StorageDelegate.setOnStartHandler(context, onStartHandler)
        StorageDelegate.setOnChangedLifecycleHandler(context, onChangedLifecycleHandler)

        callback(Result.success(Unit))
    }

    override fun configureSignalingService(
        androidNotificationName: String?,
        androidNotificationDescription: String?,
        callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.SignalingService.setNotificationTitle(context, androidNotificationName)
        StorageDelegate.SignalingService.setNotificationDescription(context, androidNotificationDescription)

        callback(Result.success(Unit))
    }

    override fun initializePushNotificationCallback(
        callbackDispatcher: Long, onNotificationSync: Long, callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.setCallbackDispatcher(context, callbackDispatcher)
        StorageDelegate.setOnNotificationSync(context, onNotificationSync)

        callback(Result.success(Unit))
    }

    override fun startService(jsonData: String?, callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "startService, data: $jsonData")

        StorageDelegate.SignalingService.setSignalingServiceEnabled(context, true)

        SignalingService.start(context, jsonData)
        callback(Result.success(Unit))
    }

    override fun stopService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "stopService")

        StorageDelegate.SignalingService.setSignalingServiceEnabled(context, false)

        SignalingService.stop(context)
        callback(Result.success(Unit))
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
