package com.webtrit.callkeep.services.core

import android.Manifest
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.foreground.ConnectionTracker
import com.webtrit.callkeep.services.services.foreground.MainProcessConnectionTracker

/**
 * In-process implementation of [CallkeepCore].
 *
 * - **State** is delegated to [MainProcessConnectionTracker] (shadow registry in the main process).
 * - **Commands** are delegated to [PhoneConnectionService] static helpers, which dispatch
 *   explicit `startService` intents or broadcasts to `:callkeep_core`.
 *
 * After the process split, replace this with a broadcast/binder-backed implementation by
 * changing only [CallkeepCore.instance] — no call sites change.
 */
class InProcessCallkeepCore private constructor() : CallkeepCore {

    private val tracker: ConnectionTracker = MainProcessConnectionTracker.instance

    // The context is read per call (not at construction time) so the singleton can be
    // created early without risking a NullPointerException. ContextHolder.init() must
    // have been called before any CS command method is invoked (guaranteed by Application.onCreate).
    private val context get() = ContextHolder.context

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    override fun exists(callId: String): Boolean = tracker.exists(callId)
    override fun isPending(callId: String): Boolean = tracker.isPending(callId)
    override fun isTerminated(callId: String): Boolean = tracker.isTerminated(callId)
    override fun isAnswered(callId: String): Boolean = tracker.isAnswered(callId)
    override fun getAll(): List<CallMetadata> = tracker.getAll()
    override fun getPendingCallIds(): Set<String> = tracker.getPendingCallIds()
    override fun get(callId: String): CallMetadata? = tracker.get(callId)
    override fun getState(callId: String): PCallkeepConnectionState? = tracker.getState(callId)
    override fun toPCallkeepConnection(callId: String): PCallkeepConnection? =
        tracker.toPCallkeepConnection(callId)

    // -------------------------------------------------------------------------
    // State mutations
    // -------------------------------------------------------------------------

    override fun addPending(callId: String): Boolean = tracker.addPending(callId)
    override fun removePending(callId: String) = tracker.removePending(callId)
    override fun promote(callId: String, metadata: CallMetadata, state: PCallkeepConnectionState) =
        tracker.promote(callId, metadata, state)
    override fun markAnswered(callId: String) = tracker.markAnswered(callId)
    override fun markHeld(callId: String, onHold: Boolean) = tracker.markHeld(callId, onHold)
    override fun markTerminated(callId: String) = tracker.markTerminated(callId)
    override fun reserveAnswer(callId: String) = tracker.reserveAnswer(callId)
    override fun consumeAnswer(callId: String): Boolean = tracker.consumeAnswer(callId)
    override fun drainUnconnectedPendingCallIds(): Set<String> =
        tracker.drainUnconnectedPendingCallIds()
    override fun clear() = tracker.clear()

    // -------------------------------------------------------------------------
    // CS commands
    // -------------------------------------------------------------------------

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    override fun startOutgoingCall(metadata: CallMetadata) =
        PhoneConnectionService.startOutgoingCall(context, metadata)

    override fun startIncomingCall(
        metadata: CallMetadata,
        onSuccess: () -> Unit,
        onError: (PIncomingCallError?) -> Unit,
    ) = PhoneConnectionService.startIncomingCall(context, metadata, onSuccess, onError)

    override fun startAnswerCall(metadata: CallMetadata) =
        PhoneConnectionService.startAnswerCall(context, metadata)

    override fun startDeclineCall(metadata: CallMetadata) =
        PhoneConnectionService.startDeclineCall(context, metadata)

    override fun startHungUpCall(metadata: CallMetadata) =
        PhoneConnectionService.startHungUpCall(context, metadata)

    override fun startEstablishCall(metadata: CallMetadata) =
        PhoneConnectionService.startEstablishCall(context, metadata)

    override fun startUpdateCall(metadata: CallMetadata) =
        PhoneConnectionService.startUpdateCall(context, metadata)

    override fun startSendDtmfCall(metadata: CallMetadata) =
        PhoneConnectionService.startSendDtmfCall(context, metadata)

    override fun startMutingCall(metadata: CallMetadata) =
        PhoneConnectionService.startMutingCall(context, metadata)

    override fun startHoldingCall(metadata: CallMetadata) =
        PhoneConnectionService.startHoldingCall(context, metadata)

    override fun startSpeaker(metadata: CallMetadata) =
        PhoneConnectionService.startSpeaker(context, metadata)

    override fun setAudioDevice(metadata: CallMetadata) =
        PhoneConnectionService.setAudioDevice(context, metadata)

    override fun tearDownService() = PhoneConnectionService.tearDown(context)

    override fun sendTearDownConnections() =
        PhoneConnectionService.sendTearDownConnections(context)

    override fun sendReserveAnswer(callId: String) =
        PhoneConnectionService.sendReserveAnswer(context, callId)

    override fun sendCleanConnections() = PhoneConnectionService.sendCleanConnections(context)

    companion object {
        val instance: CallkeepCore = InProcessCallkeepCore()
    }
}
