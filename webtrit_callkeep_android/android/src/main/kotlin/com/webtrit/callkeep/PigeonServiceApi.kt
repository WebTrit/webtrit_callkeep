package com.webtrit.callkeep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.background.BackgroundCallkeepApi
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.CallPaths
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.NotificationAction

class PigeonServiceApi(
    private val context: Context,
    api: PDelegateBackgroundServiceFlutterApi,
) : PHostBackgroundServiceApi, BroadcastReceiver() {
    private val connectionService: BackgroundCallkeepApi =
        CallkeepApiProvider.getBackgroundCallkeepApi(context, api)

    init {
        Telecom.registerPhoneAccount(context)
    }

    fun register() {
        FlutterLog.i(TAG, "register receiver ")

        // Register actions from notification
        val notificationsReceiverFilter = IntentFilter()
        notificationsReceiverFilter.addAction(NotificationAction.Hangup.action)
        notificationsReceiverFilter.addAction(NotificationAction.Answer.action)
        context.registerCustomReceiver(this, notificationsReceiverFilter)

        // Register background service
        connectionService.register()
    }

    fun unregister() {
        FlutterLog.i(TAG, "unregister receiver")
        try {
            connectionService.unregister();
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callMetaData = CallMetadata.fromBundle(intent.extras!!)

        when (intent.action) {
            NotificationAction.Hangup.action -> connectionService.endCall(callMetaData)
            NotificationAction.Answer.action -> connectionService.answer(callMetaData)
        }
    }

    override fun incomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        FlutterLog.i(TAG, "incomingCall $callId")

        val callPath = StorageDelegate.getIncomingPath(context)
        val rootPath = StorageDelegate.getRootPath(context)
        val ringtonePath = StorageDelegate.getRingtonePath(context)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            paths = CallPaths(callPath, rootPath),
            ringtonePath = ringtonePath,
            createdTime = System.currentTimeMillis()
        )

        //TODO: Fuzzy logic between two classes, in some methods the callback is called in that class in some we delegate to the implementation. Make it more clear.
        connectionService.incomingCall(callMetaData, callback)
    }

    override fun endCall(
        callId: String, callback: (Result<Unit>) -> Unit
    ) {
        FlutterLog.i(TAG, "endCall $callId")

        val callMetaData = CallMetadata(callId = callId)
        connectionService.hungUp(callMetaData, callback)

        //TODO: Should wait until all connections are removed, because the current implementation does not guarantee this
        callback.invoke(Result.success(Unit))
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        FlutterLog.i(TAG, "endAllCalls")

        connectionService.endAllCalls()

        //TODO: Should wait until all connections are removed, because the current implementation does not guarantee this
        callback.invoke(Result.success(Unit))
    }

    companion object {
        const val TAG = "PigeonServiceApi";
    }
}
