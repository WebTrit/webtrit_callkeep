package com.webtrit.callkeep

import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.core.CallkeepCore

/**
 * [PHostBackgroundPushNotificationIsolateApi] implementation used when callkeep is hosted on a
 * Flutter engine it did not create itself, registered via [WebtritCallkeep.attachToEngine].
 *
 * It is decoupled from [com.webtrit.callkeep.services.services.incoming_call.IncomingCallService]
 * and the push pathway: call-control requests are routed straight to [CallkeepCore], which owns
 * the active Telecom/standalone connection.
 */
internal class ExternalEngineCallApi : PHostBackgroundPushNotificationIsolateApi {
    override fun releaseCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        try {
            CallkeepCore.instance.startDeclineCall(CallMetadata(callId = callId))
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "releaseCall failed for callId=$callId", e)
            callback(Result.failure(e))
        }
    }

    override fun endCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        try {
            CallkeepCore.instance.startDeclineCall(CallMetadata(callId = callId))
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "endCall failed for callId=$callId", e)
            callback(Result.failure(e))
        }
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        try {
            CallkeepCore.instance.sendTearDownConnections()
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "endAllCalls failed", e)
            callback(Result.failure(e))
        }
    }

    override fun handoffCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        // The host engine owns the WebSocket in persistent/socket mode; handoff is not applicable.
        callback(Result.success(Unit))
    }

    companion object {
        private const val TAG = "ExternalEngineCallApi"
    }
}
