package com.webtrit.callkeep.services.services.incoming_call

import android.content.Context
import com.webtrit.callkeep.PCallkeepPushNotificationSyncStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.syncPushIsolate
import com.webtrit.callkeep.models.CallMetadata


interface FlutterIsolateCommunicator {
    fun performAnswer(callId: String, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit)
    fun performEndCall(callId: String, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit)
    fun notifyMissedCall(metadata: CallMetadata, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit)
    fun syncPushIsolate(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit)
    fun releaseResources(onComplete: () -> Unit)
}

class DefaultFlutterIsolateCommunicator(
    private val context: Context,
    private val serviceApi: PDelegateBackgroundServiceFlutterApi?,
    private val registerApi: PDelegateBackgroundRegisterFlutterApi?
) : FlutterIsolateCommunicator {

    override fun performAnswer(callId: String, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        serviceApi?.performAnswerCall(callId) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Service API unavailable"))
    }

    override fun performEndCall(callId: String, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        serviceApi?.performEndCall(callId) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Service API unavailable"))
    }

    override fun notifyMissedCall(metadata: CallMetadata, onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        serviceApi?.performReceivedCall  (
            metadata.callId,
            metadata.number,
            metadata.hasVideo,
            metadata.createdTime ?: System.currentTimeMillis(),
            null,
            System.currentTimeMillis()
        ) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Service API unavailable"))
    }

    override fun syncPushIsolate(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        registerApi?.syncPushIsolate(context) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Register API unavailable"))
    }

    override fun releaseResources(onComplete: () -> Unit) {
        registerApi?.onNotificationSync(
            StorageDelegate.IncomingCallService.getOnNotificationSync(context),
            PCallkeepPushNotificationSyncStatus.RELEASE_RESOURCES
        ) {
            onComplete()
        } ?: onComplete()
    }
}