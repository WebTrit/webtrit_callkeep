package com.webtrit.callkeep.services.core

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.connection.StandaloneCallService
import com.webtrit.callkeep.services.services.connection.StandaloneServiceAction

/**
 * Single routing point between the Telecom-backed and standalone call management backends.
 *
 * On devices that support `android.software.telecom`, commands are forwarded to
 * [PhoneConnectionService], which integrates with the Android Telecom framework.
 * On devices that do not (e.g. some tablets, Android Go builds, certain OEM configs),
 * commands are forwarded to [StandaloneCallService], which manages calls independently
 * via [android.media.AudioManager].
 *
 * All callers (primarily [InProcessCallkeepCore]) go through this router and have no
 * knowledge of which backend is active. Neither backend service needs routing logic —
 * each is a pure implementation of its own call management strategy.
 */
class CallServiceRouter(
    context: Context,
) {
    /** True when the device exposes `android.software.telecom`. Immutable after construction. */
    val isTelecomSupported: Boolean = TelephonyUtils.isTelecomSupported(context)

    private val ctx: Context = context.applicationContext

    // -------------------------------------------------------------------------
    // Call lifecycle
    // -------------------------------------------------------------------------

    fun startIncomingCall(
        metadata: CallMetadata,
        onSuccess: () -> Unit,
        onError: (PIncomingCallError?) -> Unit,
    ) = route(
        telecom = { PhoneConnectionService.startIncomingCall(ctx, metadata, onSuccess, onError) },
        standalone = { StandaloneCallService.startIncomingCall(ctx, metadata, onSuccess, onError) },
    )

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun startOutgoingCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startOutgoingCall(ctx, metadata) },
            standalone = { StandaloneCallService.startOutgoingCall(ctx, metadata) },
        )

    fun startAnswerCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startAnswerCall(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.AnswerCall, metadata) },
        )

    fun startDeclineCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startDeclineCall(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.DeclineCall, metadata) },
        )

    fun startHungUpCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startHungUpCall(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.HungUpCall, metadata) },
        )

    fun startEstablishCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startEstablishCall(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.EstablishCall, metadata) },
        )

    fun startUpdateCall(metadata: CallMetadata) {
        if (isTelecomSupported) PhoneConnectionService.startUpdateCall(ctx, metadata)
    }

    fun startSendDtmfCall(metadata: CallMetadata) {
        if (isTelecomSupported) PhoneConnectionService.startSendDtmfCall(ctx, metadata)
    }

    fun startMutingCall(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startMutingCall(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.Muting, metadata) },
        )

    fun startHoldingCall(metadata: CallMetadata) {
        // Hold is not supported in standalone mode.
        if (isTelecomSupported) PhoneConnectionService.startHoldingCall(ctx, metadata)
    }

    fun startSpeaker(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.startSpeaker(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.Speaker, metadata) },
        )

    fun setAudioDevice(metadata: CallMetadata) =
        route(
            telecom = { PhoneConnectionService.setAudioDevice(ctx, metadata) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.AudioDeviceSet, metadata) },
        )

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    fun tearDownService() =
        route(
            telecom = { PhoneConnectionService.tearDown(ctx) },
            standalone = { StandaloneCallService.tearDown(ctx) },
        )

    fun sendTearDownConnections() =
        route(
            telecom = { PhoneConnectionService.sendTearDownConnections(ctx) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.TearDownConnections, null) },
        )

    fun sendReserveAnswer(callId: String) =
        route(
            telecom = { PhoneConnectionService.sendReserveAnswer(ctx, callId) },
            standalone = {
                StandaloneCallService.communicate(
                    ctx,
                    StandaloneServiceAction.ReserveAnswer,
                    CallMetadata(callId = callId),
                )
            },
        )

    fun sendCleanConnections() =
        route(
            telecom = { PhoneConnectionService.sendCleanConnections(ctx) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.CleanConnections, null) },
        )

    fun sendSyncAudioState() =
        route(
            telecom = { PhoneConnectionService.sendSyncAudioState(ctx) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.SyncAudioState, null) },
        )

    fun sendSyncConnectionState() =
        route(
            telecom = { PhoneConnectionService.sendSyncConnectionState(ctx) },
            standalone = { StandaloneCallService.communicate(ctx, StandaloneServiceAction.SyncConnectionState, null) },
        )

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private inline fun route(
        telecom: () -> Unit,
        standalone: () -> Unit,
    ) {
        if (isTelecomSupported) telecom() else standalone()
    }
}
