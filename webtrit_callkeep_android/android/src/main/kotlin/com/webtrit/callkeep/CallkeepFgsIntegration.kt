package com.webtrit.callkeep

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toAudioDevice
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.services.core.CallkeepCore
import io.flutter.plugin.common.BinaryMessenger

/**
 * Registers webtrit_callkeep Pigeon channels on a secondary Flutter engine whose
 * BinaryMessenger is provided by the caller.
 *
 * ## Background
 *
 * Flutter plugins are normally registered automatically on the main engine via
 * GeneratedPluginRegistrant. Secondary engines created for background work (foreground
 * services, push-notification isolates, etc.) are typically started with
 * `automaticallyRegisterPlugins = false` to avoid initialising audio, camera, and WebRTC
 * hardware in a context where those resources must not be touched.
 *
 * Because [WebtritCallkeepPlugin.onAttachedToEngine] never fires on such engines,
 * the two Pigeon HOST APIs that callkeep's background Dart code depends on are absent:
 *
 * - [PHostBackgroundPushNotificationIsolateBootstrapApi] — used by the push-notification
 *   isolate to trigger ringing via [IncomingCallService].
 * - [PHostApi] — used by any background Dart isolate that calls [Callkeep] directly
 *   (e.g. to report an incoming call or end a call without going through the push path).
 *
 * [register] sets up both APIs on the given [messenger] so background Dart code works
 * correctly without requiring the caller to know about callkeep internals.
 *
 * ## When to call
 *
 * Call [register] once, before the background Dart isolate starts executing. A safe place
 * is inside a callback that the background engine host provides for exactly this purpose —
 * for example `SignalingForegroundService.onFgsEngineReady` from the
 * `webtrit_signaling_service` plugin, or an equivalent hook in your own foreground service.
 *
 * ```kotlin
 * // Application.onCreate — wires callkeep into the FGS engine before any Dart code runs
 * yourBackgroundEngineHost.onEngineReady = { context, messenger ->
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
        private val core get() = CallkeepCore.instance

        // setUp/tearDown manage the callkeep lifecycle and are owned by the Activity.
        // Calling them from the FGS engine would conflict with Activity-side state.
        override fun isSetUp(): Boolean = true

        override fun setUp(
            options: POptions,
            callback: (Result<Unit>) -> Unit,
        ) = callback(Result.success(Unit))

        override fun tearDown(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))

        override fun reportNewIncomingCall(
            callId: String,
            handle: PHandle,
            displayName: String?,
            hasVideo: Boolean,
            callback: (Result<PIncomingCallError?>) -> Unit,
        ) {
            val metadata =
                CallMetadata(
                    callId = callId,
                    handle = handle.toCallHandle(),
                    displayName = displayName,
                    hasVideo = hasVideo,
                    ringtonePath = StorageDelegate.Sound.getRingtonePath(context),
                )
            core.startIncomingCall(
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
            core.startDeclineCall(CallMetadata(callId = callId, displayName = displayName))
            callback(Result.success(Unit))
        }

        // No CallkeepCore command exists for the DIALING state transition.
        override fun reportConnectingOutgoingCall(
            callId: String,
            callback: (Result<Unit>) -> Unit,
        ) = callback(Result.success(Unit))

        override fun reportConnectedOutgoingCall(
            callId: String,
            callback: (Result<Unit>) -> Unit,
        ) {
            core.startEstablishCall(CallMetadata(callId = callId))
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
            core.startUpdateCall(
                CallMetadata(
                    callId = callId,
                    handle = handle?.toCallHandle(),
                    displayName = displayName,
                    hasVideo = hasVideo,
                    proximityEnabled = proximityEnabled,
                ),
            )
            callback(Result.success(Unit))
        }

        @RequiresPermission(Manifest.permission.CALL_PHONE)
        override fun startCall(
            callId: String,
            handle: PHandle,
            displayNameOrContactIdentifier: String?,
            video: Boolean,
            proximityEnabled: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startOutgoingCall(
                CallMetadata(
                    callId = callId,
                    handle = handle.toCallHandle(),
                    displayName = displayNameOrContactIdentifier,
                    hasVideo = video,
                    proximityEnabled = proximityEnabled,
                ),
            )
            callback(Result.success(null))
        }

        override fun answerCall(
            callId: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startAnswerCall(CallMetadata(callId = callId))
            callback(Result.success(null))
        }

        override fun endCall(
            callId: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startHungUpCall(CallMetadata(callId = callId))
            callback(Result.success(null))
        }

        override fun setHeld(
            callId: String,
            onHold: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startHoldingCall(CallMetadata(callId = callId, hasHold = onHold))
            callback(Result.success(null))
        }

        override fun setMuted(
            callId: String,
            muted: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startMutingCall(CallMetadata(callId = callId, hasMute = muted))
            callback(Result.success(null))
        }

        override fun setSpeaker(
            callId: String,
            enabled: Boolean,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startSpeaker(CallMetadata(callId = callId, hasSpeaker = enabled))
            callback(Result.success(null))
        }

        override fun setAudioDevice(
            callId: String,
            device: PAudioDevice,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.setAudioDevice(CallMetadata(callId = callId, audioDevice = device.toAudioDevice()))
            callback(Result.success(null))
        }

        override fun sendDTMF(
            callId: String,
            key: String,
            callback: (Result<PCallRequestError?>) -> Unit,
        ) {
            core.startSendDtmfCall(CallMetadata(callId = callId, dualToneMultiFrequency = key.firstOrNull()))
            callback(Result.success(null))
        }

        override fun onDelegateSet() {}
    }
}
