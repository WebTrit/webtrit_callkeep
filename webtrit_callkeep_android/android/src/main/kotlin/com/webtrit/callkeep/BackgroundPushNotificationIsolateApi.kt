package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

class BackgroundPushNotificationIsolateApi(
    private val context: Context
) : PHostPushNotificationIsolateApi {

    override fun initializePushNotificationCallback(
        callbackDispatcher: Long, onNotificationSync: Long, callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.BackgroundIsolate.setCallbackDispatcher(context, callbackDispatcher)
        StorageDelegate.IncomingCallService.setOnNotificationSync(context, onNotificationSync)

        callback(Result.success(Unit))
    }

    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(context)


        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath
        )
        // User press hangup or decline call
        if (PhoneConnectionService.connectionManager.isConnectionDisconnected(metadata.callId)) {
            callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED)))
        } else if (PhoneConnectionService.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            if (PhoneConnectionService.connectionManager.isConnectionAnswered(metadata.callId)) {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED)))
            } else {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS)))
            }
        } else {
            PhoneConnectionService.startIncomingCall(context, metadata)
            callback.invoke(Result.success(null))
        }

    }

    companion object {
        const val TAG = "PigeonPushNotificationIsolateApi"
    }
}
