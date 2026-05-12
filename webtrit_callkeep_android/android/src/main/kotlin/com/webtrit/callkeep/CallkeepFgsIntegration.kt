package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.services.core.CallkeepCore
import io.flutter.plugin.common.BinaryMessenger

/**
 * Registers webtrit_callkeep Pigeon channels on a secondary Flutter engine that was created
 * with automaticallyRegisterPlugins=false (e.g. the SignalingForegroundService FGS engine).
 *
 * Usage in Application.onCreate:
 * ```kotlin
 * SignalingForegroundService.onFgsEngineReady = { context, messenger ->
 *     CallkeepFgsIntegration.register(context, messenger)
 * }
 * ```
 */
object CallkeepFgsIntegration {
    fun register(
        context: Context,
        messenger: BinaryMessenger,
    ) {
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(
            messenger,
            BackgroundPushNotificationIsolateBootstrapApi(context),
        )
        PHostApi.setUp(messenger, FgsHostApiHandler(context))
    }

    private class FgsHostApiHandler(
        private val context: Context,
    ) : PHostApi {
        override fun isSetUp(): Boolean = true

        override fun setUp(
            options: POptions,
            callback: (Result<Unit>) -> Unit,
        ) {
            callback(Result.success(Unit))
        }

        override fun tearDown(callback: (Result<Unit>) -> Unit) {
            callback(Result.success(Unit))
        }

        override fun reportNewIncomingCall(
            callId: String,
            handle: PHandle,
            displayName: String?,
            hasVideo: Boolean,
            callback: (Result<PIncomingCallError?>) -> Unit,
        ) {
            val ringtonePath = StorageDelegate.Sound.getRingtonePath(context)
            val metadata =
                CallMetadata(
                    callId = callId,
                    handle = handle.toCallHandle(),
                    displayName = displayName,
                    hasVideo = hasVideo,
                    ringtonePath = ringtonePath,
                )
            CallkeepCore.instance.startIncomingCall(
                metadata = metadata,
                onSuccess = { callback(Result.success(null)) },
                onError = { error -> callback(Result.success(error)) },
            )
        }

        override fun reportEndCall(
            callId: String,
            displayName: String,
            reason: PEndCallReason,
            callback: (Result<Unit>) -> Unit,
        ) {
            val metadata = CallMetadata(callId = callId, displayName = displayName)
            CallkeepCore.instance.startDeclineCall(metadata)
            callback(Result.success(Unit))
        }

        override fun reportConnectingOutgoingCall(
            callId: String,
            callback: (Result<Unit>) -> Unit,
        ) {
            callback(Result.success(Unit))
        }

        override fun reportConnectedOutgoingCall(
            callId: String,
            callback: (Result<Unit>) -> Unit,
        ) {
            callback(Result.success(Unit))
        }

        override fun reportUpdateCall(
            callId: String,
            handle: PHandle?,
            displayName: String?,
            hasVideo: Boolean?,
            proximityEnabled: Boolean?,
            callback: (Result<Unit>) -> Unit,
        ) {
            callback(Result.success(Unit))
        }

        override fun startCall(
            callId: String,
            handle: PHandle,
            displayNameOrContactIdentifier: String?,
            video: Boolean,
            proximityEnabled: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun answerCall(
            callId: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun endCall(
            callId: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun setHeld(
            callId: String,
            onHold: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun setMuted(
            callId: String,
            muted: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun setSpeaker(
            callId: String,
            enabled: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun setAudioDevice(
            callId: String,
            device: PAudioDevice,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun sendDTMF(
            callId: String,
            key: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            callback(Result.success(null))
        }

        override fun onDelegateSet() {}
    }
}
