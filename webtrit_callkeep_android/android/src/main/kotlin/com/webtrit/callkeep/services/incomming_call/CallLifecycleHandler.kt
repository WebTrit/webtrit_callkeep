package com.webtrit.callkeep.services.incomming_call

import android.util.Log
import com.webtrit.callkeep.PHostBackgroundPushNotificationIsolateApi
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.helpers.IsolateSelector

class CallLifecycleHandler(
    private val connectionController: CallConnectionController,
    private val stopService: () -> Unit,
    private var isolateHandler: FlutterIsolateHandler
) : PHostBackgroundPushNotificationIsolateApi {

    internal var flutterApi: FlutterIsolateCommunicator? = null

    fun answerCall(metadata: CallMetadata) {
        IsolateSelector.executeBasedOnIsolate(
            mainAction = { connectionController.answer(metadata) },
            backgroundAction = { answerCallByBackground(metadata) })
    }

    fun answerCallByBackground(metadata: CallMetadata) {
        flutterApi?.performAnswer(metadata.callId, onSuccess = {
            connectionController.answer(metadata)
        }, onFailure = {
            connectionController.tearDown()
        })
    }

    fun terminateCall(metadata: CallMetadata, source: DeclineSource) {
        IsolateSelector.executeBasedOnIsolate(
            mainAction = { connectionController.hangUp(metadata) },
            backgroundAction = { declineCallByBackground(metadata, source) })
    }

    fun declineCallByBackground(metadata: CallMetadata, source: DeclineSource) {
        when (source) {
            DeclineSource.USER -> handleUserDecline(metadata)
            DeclineSource.SERVER -> handleServerDecline(metadata)
        }
    }

    fun handleMissedCall(metadata: CallMetadata) {
        flutterApi?.notifyMissedCall(metadata, onSuccess = {
            Log.d(TAG, "Missed call handled successfully")
        }, onFailure = {
            Log.e(TAG, "Missed call sync failed: $it")
            connectionController.hangUp(metadata)
            stopService()
        })
    }

    private fun handleUserDecline(metadata: CallMetadata) {
        if (isolateHandler?.isReady == true) {
            performSafeEndCall(metadata.callId, metadata)
        } else {
            flutterApi?.syncPushIsolate(onSuccess = {
                performSafeEndCall(metadata.callId, metadata)
            }, onFailure = {
                Log.e(TAG, "Sync before decline failed: $it")
                connectionController.hangUp(metadata)
                stopService()
            })
        }
    }

    private fun handleServerDecline(metadata: CallMetadata) {
        flutterApi?.releaseResources {
            connectionController.hangUp(metadata)
        }
    }

    private fun performSafeEndCall(callId: String, metadata: CallMetadata) {
        flutterApi?.performEndCall(callId, onSuccess = {
            Log.d(TAG, "Call end sent via signaling")
        }, onFailure = {
            Log.e(TAG, "Call end signaling failed: $it")
            connectionController.hangUp(metadata)
            stopService()
        })
    }

    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        terminateCall(CallMetadata(callId = callId), DeclineSource.SERVER)
        callback(Result.success(Unit))
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        flutterApi?.releaseResources {
            connectionController.tearDown()
        }
        callback(Result.success(Unit))
    }

    companion object {
        private const val TAG = "CallLifecycleHandler"
    }
}
