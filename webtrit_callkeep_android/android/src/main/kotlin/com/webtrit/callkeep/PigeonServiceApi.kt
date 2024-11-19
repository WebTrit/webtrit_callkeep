package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.background.BackgroundCallkeepApi
import com.webtrit.callkeep.common.FlutterLog
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.CallPaths
import com.webtrit.callkeep.receivers.IncomingCallNotificationReceiver
import com.webtrit.callkeep.models.toCallHandle

class PigeonServiceApi(
    private val context: Context,
    api: PDelegateBackgroundServiceFlutterApi,
) : PHostBackgroundServiceApi {
    private val connectionService: BackgroundCallkeepApi = CallkeepApiProvider.getBackgroundCallkeepApi(context, api)
    private val incomingCallReceiver = IncomingCallNotificationReceiver(
        context,
        endCall = { callMetaData -> connectionService.hungUp(callMetaData) {} },
        answerCall = { callMetaData -> connectionService.answer(callMetaData) },
    )

    init {
        Telecom.registerPhoneAccount(context)
    }

    fun register() {
        FlutterLog.i(TAG, "Registering PigeonServiceApi")

        incomingCallReceiver.registerReceiver()
        connectionService.register()
    }

    fun unregister() {
        FlutterLog.i(TAG, "Unregistering PigeonServiceApi")
        try {
            connectionService.unregister()
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering connection service: ${e.message}")
        }

        incomingCallReceiver.unregisterReceiver()
    }

    override fun incomingCall(
        callId: String, handle: PHandle, displayName: String?, hasVideo: Boolean, callback: (Result<Unit>) -> Unit
    ) {
        FlutterLog.i(TAG, "Incoming call: $callId")

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

        connectionService.incomingCall(callMetaData, callback)
    }

    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        FlutterLog.i(TAG, "End call: $callId")

        val callMetaData = CallMetadata(callId = callId)
        connectionService.hungUp(callMetaData, callback)
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        FlutterLog.i(TAG, "End all calls")

        connectionService.endAllCalls()
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    companion object {
        const val TAG = "PigeonServiceApi"
    }
}
