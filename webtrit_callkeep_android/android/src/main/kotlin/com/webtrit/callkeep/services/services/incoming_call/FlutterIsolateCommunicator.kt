package com.webtrit.callkeep.services.services.incoming_call

import android.content.Context
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.syncPushIsolate

interface FlutterIsolateCommunicator {
    fun performAnswer(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    )

    fun performEndCall(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    )

    fun syncPushIsolate(
        callData: PCallkeepIncomingCallData?,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    )
}

class DefaultFlutterIsolateCommunicator(
    private val context: Context,
    private val serviceApi: PDelegateBackgroundServiceFlutterApi?,
    private val registerApi: PDelegateBackgroundRegisterFlutterApi?,
) : FlutterIsolateCommunicator {
    override fun performAnswer(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        serviceApi?.performAnswerCall(callId) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Service API unavailable"))
    }

    override fun performEndCall(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        serviceApi?.performEndCall(callId) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Service API unavailable"))
    }

    override fun syncPushIsolate(
        callData: PCallkeepIncomingCallData?,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        registerApi?.syncPushIsolate(context, callData) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it) }
        } ?: onFailure(IllegalStateException("Register API unavailable"))
    }
}
