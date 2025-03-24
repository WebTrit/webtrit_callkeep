package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.SignalingIsolateService

class BackgroundSignalingIsolateBootstrapApi(
    private val context: Context
) : PHostBackgroundSignalingIsolateBootstrapApi {
    override fun initializeSignalingServiceCallback(
        callbackDispatcher: Long,
        onStartHandler: Long,
        onChangedLifecycleHandler: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.SignalingService.setCallbackDispatcher(context, callbackDispatcher)

        StorageDelegate.SignalingService.setOnStartHandler(context, onStartHandler)
        StorageDelegate.SignalingService.setOnChangedLifecycleHandler(context, onChangedLifecycleHandler)

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

    override fun startService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "startService")

        StorageDelegate.SignalingService.setSignalingServiceEnabled(context, true)

        SignalingIsolateService.start(context)
        callback(Result.success(Unit))
    }

    override fun stopService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "stopService")

        StorageDelegate.SignalingService.setSignalingServiceEnabled(context, false)

        SignalingIsolateService.stop(context)
        callback(Result.success(Unit))
    }

    companion object {
        const val TAG = "PigeonServiceApi"
    }
}
