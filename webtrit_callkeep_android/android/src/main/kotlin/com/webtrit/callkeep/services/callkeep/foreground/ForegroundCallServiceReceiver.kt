package com.webtrit.callkeep.services.callkeep.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.common.helpers.toPCallkeepLifecycleType
import com.webtrit.callkeep.models.ForegroundCallServiceHandles
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

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
        val handles = StorageDelegate.getForegroundCallServiceHandles(context)

        when (intent?.action) {
            ForegroundCallServiceReceiverActions.WAKE_UP.action -> onWakeUpBackgroundHandler(
                handles, intent.extras
            )

            ForegroundCallServiceReceiverActions.CHANGE_LIFECYCLE.action -> onChangedLifecycleHandler(
                intent.extras, handles
            )
        }
    }

    private fun onWakeUpBackgroundHandler(
        handles: ForegroundCallServiceHandles, extras: Bundle?
    ) {

        Log.d(TAG, "onWakeUpBackgroundHandler")

        val lifecycle = ActivityHolder.getActivityState()
        val lockScreen = Platform.isLockScreen(context)
        val pLifecycle = lifecycle.toPCallkeepLifecycleType()

        val activityReady = StorageDelegate.getActivityReady(context)
        val wakeUpHandler = handles.onStartHandler

        val jsonData = extras?.getString(PARAM_JSON_DATA) ?: "{}"

        api.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                pLifecycle,
                lockScreen,
                activityReady,
                PhoneConnectionService.connectionManager.isExistsActiveConnection(),
                jsonData
            )
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
    private fun onChangedLifecycleHandler(
        bundle: Bundle?, handles: ForegroundCallServiceHandles
    ) {
        val activityReady = StorageDelegate.getActivityReady(context)
        val lockScreen = Platform.isLockScreen(context)
        val event = bundle?.getSerializable(PARAM_CHANGE_LIFECYCLE_EVENT) as Lifecycle.Event?

        val lifecycle = (event ?: Lifecycle.Event.ON_ANY).toPCallkeepLifecycleType()
        val onChangedLifecycleHandler = handles.onChangedLifecycleHandler

        api.onApplicationStatusChanged(
            onChangedLifecycleHandler, PCallkeepServiceStatus(
                lifecycle,
                lockScreen,
                activityReady,
                PhoneConnectionService.connectionManager.isExistsActiveConnection(), "{}",
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
        private const val PARAM_CHANGE_LIFECYCLE_EVENT = "PARAM_CHANGE_LIFECYCLE_EVENT"
        private const val PARAM_JSON_DATA = "PARAM_JSON_DATA"


        fun wakeUp(context: Context, data: String?) {
            val callIntent = Intent(ForegroundCallServiceReceiverActions.WAKE_UP.action).apply {
                data?.let {
                    val bundle = Bundle().apply { putString(PARAM_JSON_DATA, it) }
                    putExtras(bundle)
                }
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
        get() = ContextHolder.appUniqueKey + name
}
