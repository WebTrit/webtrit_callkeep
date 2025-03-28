package com.webtrit.callkeep.services.incomming_call

import android.os.Handler
import android.os.Looper
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

    // Event from flutter side, as case signaling declined the call
    private fun handleServerDecline(metadata: CallMetadata) {
        connectionController.decline(metadata)
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

    // Isolate
    fun release() {
        Log.d(TAG, "Resources released")
        flutterApi?.releaseResources {
            Log.d(TAG, "Stopping service")
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Stopped service")
                stopService()
            }, SERVICE_STOP_DELAY_MS)
        } ?: run { stopService() }
    }


    // Isolate
    fun performEndCall(metadata: CallMetadata) {
        Log.d(TAG, "Resources released")
        flutterApi?.performEndCall(metadata.callId, onSuccess = {
            release()
        }, onFailure = {
            release()
        }) ?: run { stopService() }
    }


    companion object {
        private const val SERVICE_STOP_DELAY_MS = 1000L
        private const val TAG = "CallLifecycleHandler"
    }
}
