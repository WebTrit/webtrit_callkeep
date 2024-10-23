package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.common.ApplicationData
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.models.ForegroundCallServiceHandles

import com.webtrit.callkeep.services.ForegroundCallService

class PigeonIsolateApi(
    private val context: Context
) : PHostIsolateApi {

    override fun setUpCallback(
        callbackDispatcher: Long,
        onStartHandler: Long,
        onChangedLifecycleHandler: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val serviceHandles = ForegroundCallServiceHandles(
                callbackDispatcher = callbackDispatcher,
                onStartHandler = onStartHandler,
                onChangedLifecycleHandler = onChangedLifecycleHandler
            )
            StorageDelegate.setServiceHandles(context, serviceHandles)

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setUp(
        autoRestartOnTerminate: Boolean,
        autoStartOnBoot: Boolean,
        androidNotificationName: String?,
        androidNotificationDescription: String?,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val config = StorageDelegate.getForegroundCallServiceConfiguration(context).copy(
                autoStartOnBoot = autoStartOnBoot,
                autoRestartOnTerminate = autoRestartOnTerminate,
                androidNotificationName = androidNotificationName,
                androidNotificationDescription = androidNotificationDescription
            )
            StorageDelegate.setServiceConfiguration(context, config)

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }


    override fun startService(data: String, callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "startService")
        try {
            ForegroundCallService.start(context, data)

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }


    override fun stopService(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "stopService")
        try {
            ForegroundCallService.stop(context)

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun finishActivity(callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "finishActivity")
        try {
            ApplicationData.finish()

            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    companion object {
        const val TAG = "PigeonServiceApi"
    }
}
