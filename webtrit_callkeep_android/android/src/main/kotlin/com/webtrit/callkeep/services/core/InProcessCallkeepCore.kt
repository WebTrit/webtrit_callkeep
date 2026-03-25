package com.webtrit.callkeep.services.core

import android.Manifest
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata

/**
 * In-process implementation of [CallkeepCore].
 *
 * - **State** is delegated to [MainProcessConnectionTracker] (shadow registry in the main process).
 * - **Commands** are routed through [CallServiceRouter], which selects between
 *   [com.webtrit.callkeep.services.services.connection.PhoneConnectionService] (Telecom path)
 *   and [com.webtrit.callkeep.services.services.connection.StandaloneCallService] (no-Telecom path).
 *
 * All call sites are unaware of which backend is active — routing is entirely internal to [CallServiceRouter].
 */
class InProcessCallkeepCore private constructor() : CallkeepCore {
    private val tracker: ConnectionTracker = MainProcessConnectionTracker.instance

    // The context is read per call (not at construction time) so the singleton can be
    // created early without risking a NullPointerException. ContextHolder.init() must
    // have been called before any CS command method is invoked (guaranteed by Application.onCreate).
    private val context get() = ContextHolder.context

    private val router: CallServiceRouter by lazy { CallServiceRouter(context) }

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

    override fun toPCallkeepConnection(callId: String): PCallkeepConnection? = tracker.toPCallkeepConnection(callId)

    // -------------------------------------------------------------------------
    // State mutations
    // -------------------------------------------------------------------------

    override fun addPending(callId: String): Boolean = tracker.addPending(callId)

    override fun removePending(callId: String) = tracker.removePending(callId)

    override fun promote(
        callId: String,
        metadata: CallMetadata,
        state: PCallkeepConnectionState,
    ) = tracker.promote(callId, metadata, state)

    override fun markAnswered(callId: String) = tracker.markAnswered(callId)

    override fun markHeld(
        callId: String,
        onHold: Boolean,
    ) = tracker.markHeld(callId, onHold)

    override fun markTerminated(callId: String) = tracker.markTerminated(callId)

    override fun reserveAnswer(callId: String) = tracker.reserveAnswer(callId)

    override fun consumeAnswer(callId: String): Boolean = tracker.consumeAnswer(callId)

    override fun drainUnconnectedPendingCallIds(): Set<String> = tracker.drainUnconnectedPendingCallIds()

    override fun clear() = tracker.clear()

    // -------------------------------------------------------------------------
    // Callback guards
    // -------------------------------------------------------------------------

    override fun markDirectNotified(callId: String) = tracker.markDirectNotified(callId)

    override fun consumeDirectNotified(callId: String): Boolean = tracker.consumeDirectNotified(callId)

    override fun markEndCallDispatched(callId: String): Boolean = tracker.markEndCallDispatched(callId)

    override fun markSignalingRegistered(callId: String) = tracker.markSignalingRegistered(callId)

    override fun consumeSignalingRegistered(callId: String): Boolean = tracker.consumeSignalingRegistered(callId)

    // -------------------------------------------------------------------------
    // CS commands
    // -------------------------------------------------------------------------

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    override fun startOutgoingCall(metadata: CallMetadata) = router.startOutgoingCall(metadata)

    override fun startIncomingCall(
        metadata: CallMetadata,
        onSuccess: () -> Unit,
        onError: (PIncomingCallError?) -> Unit,
    ) {
        // Register as pending before handing off to the backend so that answerCall() can
        // find the call via core.isPending() during the broadcast-lag window, regardless
        // of which entry point initiated the incoming call (ForegroundService,
        // SignalingIsolateService, BackgroundPushNotificationIsolateBootstrapApi, etc.).
        // addPending() is idempotent — safe to call even if the caller already did so.
        tracker.addPending(metadata.callId)
        router.startIncomingCall(metadata, onSuccess, onError)
    }

    override fun startAnswerCall(metadata: CallMetadata) = router.startAnswerCall(metadata)

    override fun startDeclineCall(metadata: CallMetadata) = router.startDeclineCall(metadata)

    override fun startHungUpCall(metadata: CallMetadata) = router.startHungUpCall(metadata)

    override fun startEstablishCall(metadata: CallMetadata) = router.startEstablishCall(metadata)

    override fun startUpdateCall(metadata: CallMetadata) = router.startUpdateCall(metadata)

    override fun startSendDtmfCall(metadata: CallMetadata) = router.startSendDtmfCall(metadata)

    override fun startMutingCall(metadata: CallMetadata) = router.startMutingCall(metadata)

    override fun startHoldingCall(metadata: CallMetadata) = router.startHoldingCall(metadata)

    override fun startSpeaker(metadata: CallMetadata) = router.startSpeaker(metadata)

    override fun setAudioDevice(metadata: CallMetadata) = router.setAudioDevice(metadata)

    override fun tearDownService() = router.tearDownService()

    override fun sendTearDownConnections() = router.sendTearDownConnections()

    override fun sendReserveAnswer(callId: String) = router.sendReserveAnswer(callId)

    override fun sendCleanConnections() = router.sendCleanConnections()

    override fun sendSyncAudioState() = router.sendSyncAudioState()

    override fun sendSyncConnectionState() = router.sendSyncConnectionState()

    companion object {
        val instance: CallkeepCore = InProcessCallkeepCore()
    }
}
