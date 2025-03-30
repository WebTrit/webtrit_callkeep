package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService

class BackgroundPushNotificationIsolateBootstrapApi(
    private val context: Context
) : PHostBackgroundPushNotificationIsolateBootstrapApi {
    override fun initializePushNotificationCallback(
        callbackDispatcher: Long, onNotificationSync: Long, callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.IncomingCallService.setCallbackDispatcher(context, callbackDispatcher)
        StorageDelegate.IncomingCallService.setOnNotificationSync(context, onNotificationSync)

        callback(Result.success(Unit))
    }

    override fun configureSignalingService(
        launchBackgroundIsolateEvenIfAppIsOpen: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        StorageDelegate.IncomingCallService.setLaunchBackgroundIsolateEvenIfAppIsOpen(
            context,
            launchBackgroundIsolateEvenIfAppIsOpen
        )

        callback(Result.success(Unit))
    }

    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        Log.d(TAG, "reportNewIncomingCall: $callId, $handle, $displayName, $hasVideo")
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(context)

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath
        )

        PhoneConnectionService.startIncomingCall(
            context = context,
            metadata = metadata,
            onSuccess = { callback(Result.success(null)) },
            onError = { error -> callback(Result.success(error)) })
    }

    companion object {
        const val TAG = "PigeonPushNotificationIsolateApi"
    }
}
