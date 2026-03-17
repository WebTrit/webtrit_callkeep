package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHandleTypeEnum
import com.webtrit.callkeep.PHostBackgroundPushNotificationIsolateApi
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.common.IsolateSelector
import com.webtrit.callkeep.services.services.incoming_call.CallConnectionController
import com.webtrit.callkeep.services.services.incoming_call.FlutterIsolateCommunicator

enum class DeclineSource {
    USER, SERVER
}

class CallLifecycleHandler(
    private val connectionController: CallConnectionController,
    private val stopService: () -> Unit,
    private var isolateHandler: FlutterIsolateHandler
) : PHostBackgroundPushNotificationIsolateApi {

    internal var flutterApi: FlutterIsolateCommunicator? = null

    var currentCallData: PCallkeepIncomingCallData? = null

    // Notify connection service about answering the call
    fun reportAnswerToConnectionService(metadata: CallMetadata) {
        connectionController.answer(metadata)
    }

    // Notify connection service about declining the call
    fun reportDeclineToConnectionService(metadata: CallMetadata) {
        connectionController.decline(metadata)
    }

    // Connection service event for answering the call, synchronized with Flutter if the app is in the background.
    // Does NOT call connectionController.answer() on success: Telecom already confirmed the answer by
    // invoking this method; a second answer() call would send a duplicate signal to the connection service.
    fun performAnswerCall(metadata: CallMetadata) {
        IsolateSelector.executeIfBackground {
            val api = flutterApi
            if (api == null) {
                Log.w(TAG, "performAnswerCall: flutterApi is null, no Flutter isolate to notify for callId=${metadata.callId}")
                return@executeIfBackground
            }
            api.performAnswer(metadata.callId, onSuccess = {
                Log.d(TAG, "performAnswerCall: Flutter isolate acknowledged answer for callId=${metadata.callId}")
            }, onFailure = {
                Log.d(TAG, "Tear down connection due to answer failure: $it")
                connectionController.tearDown()
            })
        }
    }

    fun performEndCall(metadata: CallMetadata) {
        val api = flutterApi
        if (api != null) {
            api.performEndCall(
                metadata.callId,
                onSuccess = { release() },
                onFailure = { release() })
        } else {
            Log.w(TAG, "performEndCall: flutterApi is null, releasing resources directly")
            release()
        }
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

    private fun handleUserDecline(metadata: CallMetadata) {
        if (isolateHandler.isReady == true) {
            performSafeEndCall(metadata.callId, metadata)
        } else {
            flutterApi?.syncPushIsolate(currentCallData, onSuccess = {
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
        flutterApi?.releaseResources(currentCallData) {
            connectionController.tearDown()
        }
        callback(Result.success(Unit))
    }

    // Isolate
    fun release() {
        Log.d(TAG, "Resources released")
        flutterApi?.releaseResources(currentCallData) {
            stopServiceWithDelay()
        } ?: run { stopService() }
    }

    private fun stopServiceWithDelay() {
        Log.d(TAG, "Stopping service")
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Stopped service")
            stopService()
        }, SERVICE_STOP_DELAY_MS)
    }

    companion object {
        private const val SERVICE_STOP_DELAY_MS = 1000L
        private const val TAG = "CallLifecycleHandler"
    }
}
