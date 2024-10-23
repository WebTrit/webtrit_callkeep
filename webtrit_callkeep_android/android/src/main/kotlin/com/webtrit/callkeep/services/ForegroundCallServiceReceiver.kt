package com.webtrit.callkeep.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepLifecycleType
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.common.ApplicationData
import com.webtrit.callkeep.common.Constants
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.common.helpers.toPCallkeepLifecycleType
import com.webtrit.callkeep.common.models.ForegroundCallServiceConfig

class ForegroundCallServiceReceiver(
    private val api: PDelegateBackgroundRegisterFlutterApi,
    private val context: Context,
) : BroadcastReceiver() {

    /**
     * Registers this receiver with the provided Android context.
     *
     * @param context The Android context in which to register the receiver.
     */
    fun registerReceiver(context: Context) {
        Log.d(TAG, "ForegroundCallServiceReceiver:registerReceiver")

        val intentFilter = IntentFilter()
        intentFilter.addAction(ForegroundCallServiceReceiverActions.WAKE_UP.action)
        intentFilter.addAction(ForegroundCallServiceReceiverActions.CHANGE_LIFECYCLE.action)

        context.registerCustomReceiver(this, intentFilter)
    }


    fun unregisterReceiver(context: Context) {
        Log.d(TAG, "ForegroundCallServiceReceiver:unregisterReceiver")
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val config = StorageDelegate.getForegroundCallServiceConfiguration(context)

        when (intent?.action) {
            ForegroundCallServiceReceiverActions.WAKE_UP.action -> onWakeUpBackgroundHandler(intent.extras, config)
            ForegroundCallServiceReceiverActions.CHANGE_LIFECYCLE.action -> onChangedLifecycleHandler(
                intent.extras,
                config
            )
        }
    }

    private fun onWakeUpBackgroundHandler(extras: Bundle?, config: ForegroundCallServiceConfig) {
        val lifecycle = ApplicationData.getActivityState()
        val lockScreen = Platform.isLockScreen(context)
        val pLifecycle = lifecycle?.toPCallkeepLifecycleType() ?: PCallkeepLifecycleType.ON_ANY

        val activityReady = StorageDelegate.getActivityReady(context)
        val wakeUpHandler = config.onStartHandler ?: throw Exception("onStartHandler is not set")
        val data = extras?.getString(PARAM_WAKE_UP_DATA) ?: Constants.EMPTY_JSON_MAP

        api.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                pLifecycle, config.autoRestartOnTerminate, config.autoStartOnBoot, lockScreen, activityReady
            ), data
        ) { response ->
            response.onSuccess {
                Log.d(TAG, "onWakeUpBackgroundHandler: $it")
            }
            response.onFailure {
                Log.e(TAG, "onWakeUpBackgroundHandler: $it")
            }
        }
    }

    @Suppress("DEPRECATION", "KotlinConstantConditions")
    private fun onChangedLifecycleHandler(bundle: Bundle?, config: ForegroundCallServiceConfig) {
        val activityReady = StorageDelegate.getActivityReady(context)
        val lockScreen = Platform.isLockScreen(context)
        val event = bundle?.getSerializable(PARAM_CHANGE_LIFECYCLE_EVENT) as Lifecycle.Event?

        val lifecycle = (event ?: Lifecycle.Event.ON_ANY).toPCallkeepLifecycleType()
        val onChangedLifecycleHandler = config.onChangedLifecycleHandler ?: throw Exception("onStartHandler is not set")

        api.onApplicationStatusChanged(
            onChangedLifecycleHandler, PCallkeepServiceStatus(
                lifecycle, config.autoRestartOnTerminate, config.autoStartOnBoot, lockScreen, activityReady
            )
        ) { response ->
            response.onSuccess {
                Log.d(TAG, "appChanged: $it")
            }
            response.onFailure {
                Log.e(TAG, "appChanged: $it")
            }
        }
    }

    companion object {
        private const val TAG = "ForegroundCallServiceReceiver"

        private const val PARAM_WAKE_UP_DATA = "PARAM_WAKE_UP_DATA"
        private const val PARAM_CHANGE_LIFECYCLE_EVENT = "PARAM_CHANGE_LIFECYCLE_EVENT"

        fun wakeUp(context: Context, data: String) {
            val callIntent = Intent(ForegroundCallServiceReceiverActions.WAKE_UP.action).apply {
                putExtra(PARAM_WAKE_UP_DATA, data)
            }
            context.sendBroadcast(callIntent)
        }


        fun changeLifecycle(context: Context, event: Lifecycle.Event) {
            val intent = Intent(ForegroundCallServiceReceiverActions.CHANGE_LIFECYCLE.action).apply {
                putExtra(PARAM_CHANGE_LIFECYCLE_EVENT, event)
            }
            context.sendBroadcast(intent)
        }
    }
}

enum class ForegroundCallServiceReceiverActions {
    WAKE_UP, CHANGE_LIFECYCLE;

    val action: String
        get() = ApplicationData.appUniqueKey + name
}
