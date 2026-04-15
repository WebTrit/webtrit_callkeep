package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.PHostBackgroundPushNotificationIsolateApi
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.core.CallkeepCore
import com.webtrit.callkeep.services.services.incoming_call.CallConnectionController
import com.webtrit.callkeep.services.services.incoming_call.FlutterIsolateCommunicator

enum class DeclineSource {
    USER,
    SERVER,
}

class CallLifecycleHandler(
    private val connectionController: CallConnectionController,
    private val stopService: () -> Unit,
    private var isolateHandler: FlutterIsolateHandler,
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
        val api = flutterApi
        if (api == null) {
            Log.w(TAG, "performAnswerCall: flutterApi is null, no Flutter isolate to notify for callId=${metadata.callId}")
            return
        }
        api.performAnswer(metadata.callId, onSuccess = {
            Log.d(TAG, "performAnswerCall: Flutter isolate acknowledged answer for callId=${metadata.callId}")
        }, onFailure = {
            Log.d(TAG, "Tear down connection due to answer failure: $it")
            connectionController.tearDown()
        })
    }

    fun performEndCall(metadata: CallMetadata) {
        val api = flutterApi
        if (api != null) {
            api.performEndCall(
                metadata.callId,
                onSuccess = { release() },
                onFailure = { release() },
            )
        } else {
            Log.w(TAG, "performEndCall: flutterApi is null, releasing resources directly")
            release()
        }
    }

    fun terminateCall(
        metadata: CallMetadata,
        source: DeclineSource,
    ) {
        declineCallByBackground(metadata, source)
    }

    fun declineCallByBackground(
        metadata: CallMetadata,
        source: DeclineSource,
    ) {
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

    private fun performSafeEndCall(
        callId: String,
        metadata: CallMetadata,
    ) {
        flutterApi?.performEndCall(callId, onSuccess = {
            Log.d(TAG, "Call end sent via signaling")
        }, onFailure = {
            Log.e(TAG, "Call end signaling failed: $it")
            connectionController.hangUp(metadata)
            stopService()
        })
    }

    override fun endCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        terminateCall(CallMetadata(callId = callId), DeclineSource.SERVER)
        callback(Result.success(Unit))
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        connectionController.tearDown()
        callback(Result.success(Unit))
    }

    override fun releaseCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        // releaseCall() is called by the push-notification isolate when it is done with
        // background processing (signaling sync, missed-call notification, call log).
        // It must NOT terminate the Telecom connection when the call is still active or
        // pending — doing so declines a call that the user has not yet interacted with.
        //
        // This happens when the push-notification isolate connects to the signaling hub
        // and receives a handshake with no active lines (e.g. the main-process signaling
        // session already consumed the IncomingCallEvent, or a duplicate FCM delivery
        // triggers a second isolate run). In that case _onNoActiveLines() fires and calls
        // releaseCall(), but Telecom still has the call in RINGING state.
        //
        // Guard: if the call is promoted or pending in CallkeepCore, stop the FGS only.
        // The Telecom connection stays alive and the user can still answer or decline.
        val core = CallkeepCore.instance
        val isActiveOrPending =
            core.getAll().any { it.callId == callId } || core.getPendingCallIds().contains(callId)

        if (isActiveOrPending) {
            Log.d(TAG, "releaseCall: $callId is active/pending in Telecom — stopping service only, skipping terminate")
        } else {
            Log.d(TAG, "releaseCall: $callId - no active connection, terminating and stopping service")
            terminateCall(CallMetadata(callId = callId), DeclineSource.SERVER)
        }
        stopService()
        callback(Result.success(Unit))
    }

    fun release(onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "Resources released")
        onComplete?.invoke()
        stopServiceWithDelay()
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
