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
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
                CallLifecycleEvent.DidPushIncomingCall,
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
