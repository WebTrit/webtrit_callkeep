package com.webtrit.callkeep.services.core

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallConnectionState
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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
class InProcessCallkeepCore internal constructor(
    private val tracker: ConnectionTracker = MainProcessConnectionTracker.instance,
    routerInit: () -> CallServiceRouter = { CallServiceRouter(ContextHolder.context) },
) : CallkeepCore {
    // The context is read per call (not at construction time) so the singleton can be
    // created early without risking a NullPointerException. ContextHolder.init() must
    // have been called before any CS command method is invoked (guaranteed by Application.onCreate).
    private val context get() = ContextHolder.context

    private val router: CallServiceRouter by lazy(routerInit)

    // -------------------------------------------------------------------------
    // Listener registry and lazy global BroadcastReceiver
    // -------------------------------------------------------------------------

    private val listeners = CopyOnWriteArrayList<ConnectionEventListener>()
    private var globalReceiver: BroadcastReceiver? = null
    private val receiverLock = Any()

    // Tracks per-call receivers registered via registerConnectionEvents so that
    // notifyConnectionEvent can deliver events in-process without going through AMS.
    private val inProcessReceivers = ConcurrentHashMap<BroadcastReceiver, List<String>>()

    override fun addConnectionEventListener(listener: ConnectionEventListener) {
        listeners.add(listener)
        synchronized(receiverLock) {
            if (globalReceiver == null) {
                globalReceiver =
                    createGlobalReceiver().also { receiver ->
                        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
                            GLOBAL_LISTENER_EVENTS,
                            context,
                            receiver,
                            exported = false,
                        )
                    }
            }
        }
    }

    override fun removeConnectionEventListener(listener: ConnectionEventListener) {
        listeners.remove(listener)
        synchronized(receiverLock) {
            if (listeners.isEmpty()) {
                globalReceiver?.let { receiver ->
                    runCatching {
                        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(context, receiver)
                    }
                }
                globalReceiver = null
            }
        }
    }

    private fun createGlobalReceiver(): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                ctx: Context?,
                intent: Intent?,
            ) {
                val action = intent?.action ?: return
                val event = GLOBAL_LISTENER_EVENTS.find { it.name == action } ?: return
                listeners.forEach { it.onConnectionEvent(event, intent.extras) }
            }
        }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    override fun exists(callId: String): Boolean = tracker.exists(callId)

    override fun isPending(callId: String): Boolean = tracker.isPending(callId)

    override fun isTerminated(callId: String): Boolean = tracker.isTerminated(callId)

    override fun isAnswered(callId: String): Boolean = tracker.isAnswered(callId)

    override fun checkIncomingDuplicate(callId: String): PIncomingCallError? =
        when {
            tracker.isAnswered(callId) -> PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED)
            tracker.exists(callId) -> PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS)
            else -> null
        }

    override fun routeAnswerCall(callId: String): AnswerCallRoute =
        when {
            tracker.exists(callId) -> AnswerCallRoute.AnswerImmediately
            tracker.isPending(callId) -> AnswerCallRoute.DeferAnswer
            else -> AnswerCallRoute.NotFound
        }

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

    override fun updateState(
        callId: String,
        state: CallConnectionState,
    ) = tracker.updateState(callId, state)

    override fun markTerminated(callId: String) = tracker.markTerminated(callId)

    override fun clearAndMarkEndCallDispatched(callId: String): Boolean {
        tracker.markTerminated(callId)
        // Remove the main-process ConnectionManager pending reservation so a subsequent
        // reportNewIncomingCall with the same callId (e.g. blind transfer-back) is not
        // permanently blocked by the stale pendingCallIds entry from the original registration.
        PhoneConnectionService.connectionManager.removePending(callId)
        return tracker.markEndCallDispatched(callId)
    }

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

    override fun markReportedIncoming(callId: String) = tracker.markReportedIncoming(callId)

    override fun consumeReportedIncoming(callId: String): Boolean = tracker.consumeReportedIncoming(callId)

    // -------------------------------------------------------------------------
    // Connection event receivers
    // -------------------------------------------------------------------------

    override fun registerConnectionEvents(
        context: Context,
        events: List<ConnectionEvent>,
        receiver: BroadcastReceiver,
        exported: Boolean,
    ): IntentFilter {
        inProcessReceivers[receiver] = events.map { it.name }
        return ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(events, context, receiver, exported)
    }

    override fun unregisterConnectionEvents(
        context: Context,
        receiver: BroadcastReceiver,
    ) {
        inProcessReceivers.remove(receiver)
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(context, receiver)
    }

    override fun notifyConnectionEvent(
        event: ConnectionEvent,
        data: Bundle?,
    ) {
        val actionName = event.name
        val intent = Intent(actionName).apply { data?.let { putExtras(it) } }

        // Deliver to global listeners (ForegroundService, IncomingCallService, etc.)
        listeners.forEach { it.onConnectionEvent(event, data) }

        // Deliver to per-call dynamic receivers (OngoingCall, TearDownComplete, etc.)
        inProcessReceivers.entries.toList().forEach { (receiver, actions) ->
            if (actionName in actions) {
                receiver.onReceive(context, intent)
            }
        }
    }

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
        val callId = metadata.callId
        // Reserve the pendingCallIds entry before handing off to the backend so that
        // answerCall() / endCall() issued before IncomingConnectionReported fires can locate
        // the call via core.isPending() during the broadcast-lag window.
        //
        // addPending() returns true only when this invocation actually inserted the entry.
        // If it returns false, a concurrent first invocation (e.g. push-isolate vs
        // foreground signaling for the same callId) already owns the entry — reject this
        // duplicate via onError(CALL_ID_ALREADY_EXISTS) rather than letting both proceed
        // to Telecom, which would cause the second to be silently adopted via the
        // :callkeep_core CALL_ID_ALREADY_EXISTS path and return null, masking the
        // duplicate from the caller.
        val addedPending = tracker.addPending(callId)
        if (!addedPending) {
            Log.w(TAG, "startIncomingCall: callId=$callId already pending, rejecting concurrent duplicate")
            onError(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS))
            return
        }

        // From here on we own the pendingCallIds entry. Any failure must drain it so the
        // next reportNewIncomingCall for the same callId is not rejected as a stale duplicate.
        val drained = AtomicBoolean(false)

        fun drainOnce() {
            if (drained.compareAndSet(false, true)) {
                tracker.removePending(callId)
            }
        }

        try {
            router.startIncomingCall(
                metadata,
                onSuccess = onSuccess,
                onError = { err ->
                    drainOnce()
                    onError(err)
                },
            )
        } catch (t: Throwable) {
            // Synchronous failure (e.g. IllegalStateException from ContextHolder, SecurityException,
            // any unexpected throw inside the router). Drain so the next reportNewIncomingCall for
            // this callId is not rejected as "already pending, rejecting concurrent duplicate".
            //
            // The throwable is re-thrown rather than converted to onError so the original
            // exception message + stack trace reach Dart via Pigeon's channel-error envelope
            // (better diagnostics than a structured PIncomingCallError(INTERNAL) that would
            // lose t.message). This skips the onError callback path — callers that pre-register
            // state before invoking this method must handle that themselves; see KDoc on
            // CallkeepCore.startIncomingCall.
            drainOnce()
            throw t
        }
    }

    override fun startAnswerCall(metadata: CallMetadata) = router.startAnswerCall(metadata)

    override fun startDeclineCall(metadata: CallMetadata) = router.startDeclineCall(metadata)

    override fun startHungUpCall(metadata: CallMetadata) = router.startHungUpCall(metadata)

    override fun startEstablishCall(metadata: CallMetadata) = router.startEstablishCall(metadata)

    override fun startUpdateCall(metadata: CallMetadata) {
        tracker.updateMetadata(metadata)
        router.startUpdateCall(metadata)
    }

    override fun startSendDtmfCall(metadata: CallMetadata) = router.startSendDtmfCall(metadata)

    override fun startMutingCall(metadata: CallMetadata) = router.startMutingCall(metadata)

    override fun startHoldingCall(metadata: CallMetadata) = router.startHoldingCall(metadata)

    override fun startSpeaker(metadata: CallMetadata) = router.startSpeaker(metadata)

    override fun setAudioDevice(metadata: CallMetadata) = router.setAudioDevice(metadata)

    override fun tearDownService() = router.tearDownService()

    override fun sendTearDownConnections() = router.sendTearDownConnections()

    override fun sendReserveAnswer(callId: String) = router.sendReserveAnswer(callId)

    override fun sendCleanConnections() = router.sendCleanConnections()

    override fun replayAudioState() = router.replayAudioState()

    override fun replayConnectionStates() = router.replayConnectionStates()

    companion object {
        private const val TAG = "InProcessCallkeepCore"

        val instance: CallkeepCore = InProcessCallkeepCore()

        /**
         * Events delivered to all [ConnectionEventListener] subscribers.
         *
         * Covers the persistent global subscriptions used by [ForegroundService] and
         * [IncomingCallService]. Per-call one-off receivers (OngoingCall, OutgoingFailure,
         * TearDownComplete, IncomingFailure) are excluded — they stay as dynamic
         * receivers registered via [registerConnectionEvents].
         */
        internal val GLOBAL_LISTENER_EVENTS: List<ConnectionEvent> =
            listOf(
                CallLifecycleEvent.IncomingConnectionReported,
                CallLifecycleEvent.ConnectionStateChanged,
                CallLifecycleEvent.DeclineCall,
                CallLifecycleEvent.HungUp,
                CallLifecycleEvent.ConnectionNotFound,
                CallLifecycleEvent.AnswerCall,
                CallMediaEvent.AudioDeviceSet,
                CallMediaEvent.AudioDevicesUpdate,
                CallMediaEvent.AudioMuting,
                CallMediaEvent.ConnectionHolding,
                CallMediaEvent.SentDTMF,
            )
    }
}
