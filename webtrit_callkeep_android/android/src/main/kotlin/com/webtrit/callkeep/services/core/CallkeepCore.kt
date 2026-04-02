package com.webtrit.callkeep.services.core

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent

/**
 * Routing result for [CallkeepCore.routeAnswerCall].
 *
 * Encodes which action [ForegroundService.answerCall] should take without
 * leaking state-query details outside the facade.
 */
sealed class AnswerCallRoute {
    /** A live [PhoneConnection] exists — answer via IPC immediately. */
    object AnswerImmediately : AnswerCallRoute()

    /** Telecom accepted the call but [PhoneConnection] is not yet created — defer via ReserveAnswer. */
    object DeferAnswer : AnswerCallRoute()

    /** No connection or pending entry found for this callId. */
    object NotFound : AnswerCallRoute()
}

/**
 * Receives connection events dispatched by [CallkeepCore].
 *
 * Register via [CallkeepCore.addConnectionEventListener]; unregister via
 * [CallkeepCore.removeConnectionEventListener]. The callback is invoked on the main thread.
 */
fun interface ConnectionEventListener {
    fun onConnectionEvent(
        event: ConnectionEvent,
        data: Bundle?,
    )
}

/**
 * Single facade for all interactions with the `:callkeep_core` process.
 *
 * Replaces two separate access points:
 * - `ConnectionTracker` (read/write shadow state in the main process), and
 * - `PhoneConnectionService` static methods (commands sent to `:callkeep_core`).
 *
 * The [companion] exposes a process-wide [instance]. After the process split, swap
 * [InProcessCallkeepCore] for a broadcast/binder-backed implementation by changing
 * only the [instance] assignment — no call sites change.
 *
 * ## Method groups
 *
 * **State queries** — read the main-process shadow of connection state.
 * **State mutations** — update the shadow as broadcasts arrive from `:callkeep_core`.
 * **CS commands** — send actions to `:callkeep_core` (via `startService` or broadcast).
 */
interface CallkeepCore {
    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    fun exists(callId: String): Boolean

    fun isPending(callId: String): Boolean

    fun isTerminated(callId: String): Boolean

    fun isAnswered(callId: String): Boolean

    /**
     * Checks whether a new incoming call for [callId] should be rejected due to an
     * existing active or answered call with the same ID.
     *
     * Returns [PIncomingCallError] with [PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED]
     * if the call was already answered, [PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS] if it is
     * still ringing/active, or null if the callId is free and the new call may proceed.
     *
     * Centralises the duplicate-detection logic from [ForegroundService.reportNewIncomingCall]
     * so that ForegroundService operates on intent (reject/allow) rather than raw state flags.
     */
    fun checkIncomingDuplicate(callId: String): PIncomingCallError?

    /**
     * Routes an answer request for [callId] based on current shadow state.
     *
     * Returns [AnswerCallRoute.AnswerImmediately] if the call is already promoted,
     * [AnswerCallRoute.DeferAnswer] if it is pending but has no PhoneConnection yet,
     * or [AnswerCallRoute.NotFound] if the callId is unknown.
     *
     * Centralises the routing logic from [ForegroundService.answerCall].
     */
    fun routeAnswerCall(callId: String): AnswerCallRoute

    fun getAll(): List<CallMetadata>

    fun getPendingCallIds(): Set<String>

    fun get(callId: String): CallMetadata?

    fun getState(callId: String): PCallkeepConnectionState?

    fun toPCallkeepConnection(callId: String): PCallkeepConnection?

    // -------------------------------------------------------------------------
    // State mutations
    // -------------------------------------------------------------------------

    /** Returns true if the callId was not already present (i.e. this call actually added it). */
    fun addPending(callId: String): Boolean

    fun removePending(callId: String)

    fun promote(
        callId: String,
        metadata: CallMetadata,
        state: PCallkeepConnectionState,
    )

    fun markAnswered(callId: String)

    fun markHeld(
        callId: String,
        onHold: Boolean,
    )

    fun markTerminated(callId: String)

    /**
     * Atomically marks [callId] as terminated and records that [performEndCall] has been
     * dispatched for it. Equivalent to calling [markTerminated] then [markEndCallDispatched]
     * in sequence, but prevents the two from being accidentally split.
     *
     * Returns true if this is the first dispatch (i.e. [markEndCallDispatched] returned true),
     * false if [performEndCall] was already dispatched for this call.
     */
    fun markTerminatedWithEndCall(callId: String): Boolean

    /** Reserves a deferred answer in the main-process shadow. */
    fun reserveAnswer(callId: String)

    fun consumeAnswer(callId: String): Boolean

    /**
     * Returns pending call IDs that have no promoted connection yet, and removes them
     * from the pending set so they are not returned again.
     */
    fun drainUnconnectedPendingCallIds(): Set<String>

    fun clear()

    // -------------------------------------------------------------------------
    // Callback guards
    // -------------------------------------------------------------------------

    fun markDirectNotified(callId: String)

    fun consumeDirectNotified(callId: String): Boolean

    fun markEndCallDispatched(callId: String): Boolean

    fun markSignalingRegistered(callId: String)

    fun consumeSignalingRegistered(callId: String): Boolean

    // -------------------------------------------------------------------------
    // Connection event receivers
    // -------------------------------------------------------------------------

    /**
     * Subscribes [listener] to receive connection events from [ConnectionServicePerformBroadcaster].
     *
     * The first registered listener triggers registration of a single global [BroadcastReceiver]
     * that lives until the last listener unregisters. All subsequent listeners share that receiver.
     * Safe to call multiple times with the same listener — duplicates are allowed by
     * [java.util.concurrent.CopyOnWriteArrayList] semantics; callers must balance
     * add/remove calls.
     */
    fun addConnectionEventListener(listener: ConnectionEventListener)

    /**
     * Unsubscribes [listener]. When the last listener is removed the global [BroadcastReceiver]
     * is unregistered.
     */
    fun removeConnectionEventListener(listener: ConnectionEventListener)

    /**
     * Registers [receiver] to receive the given [events] from [ConnectionServicePerformBroadcaster].
     * Use this for temporary per-call receivers (e.g. waiting for one specific confirmation
     * broadcast). For persistent subscriptions prefer [addConnectionEventListener].
     */
    fun registerConnectionEvents(
        context: Context,
        events: List<ConnectionEvent>,
        receiver: BroadcastReceiver,
        exported: Boolean = false,
    ): IntentFilter

    /**
     * Unregisters a [receiver] previously registered via [registerConnectionEvents].
     */
    fun unregisterConnectionEvents(
        context: Context,
        receiver: BroadcastReceiver,
    )

    /**
     * Delivers [event] directly to all registered [ConnectionEventListener]s and any
     * per-call receivers registered via [registerConnectionEvents], without going through
     * [android.app.ActivityManager] broadcast dispatch.
     *
     * Used by [com.webtrit.callkeep.services.services.connection.StandaloneCallService],
     * which runs in the main process alongside [ForegroundService]. On certain OEM devices
     * (e.g. Lenovo TB300FU, Android 13) the system ActivityManager suppresses all
     * [android.content.Context.sendBroadcast] calls originating from the app, so
     * in-process delivery is required.
     */
    fun notifyConnectionEvent(
        event: ConnectionEvent,
        data: Bundle? = null,
    )

    // -------------------------------------------------------------------------
    // CS commands
    // -------------------------------------------------------------------------

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun startOutgoingCall(metadata: CallMetadata)

    fun startIncomingCall(
        metadata: CallMetadata,
        onSuccess: () -> Unit,
        onError: (PIncomingCallError?) -> Unit,
    )

    fun startAnswerCall(metadata: CallMetadata)

    fun startDeclineCall(metadata: CallMetadata)

    fun startHungUpCall(metadata: CallMetadata)

    fun startEstablishCall(metadata: CallMetadata)

    fun startUpdateCall(metadata: CallMetadata)

    fun startSendDtmfCall(metadata: CallMetadata)

    fun startMutingCall(metadata: CallMetadata)

    fun startHoldingCall(metadata: CallMetadata)

    fun startSpeaker(metadata: CallMetadata)

    fun setAudioDevice(metadata: CallMetadata)

    /**
     * Sends the legacy [ServiceAction.TearDown] intent to [PhoneConnectionService].
     * This resets the service state for the next session without hanging up connections
     * (connections are expected to be already torn down via [sendTearDownConnections]).
     */
    fun tearDownService()

    /**
     * Sends [ServiceAction.TearDownConnections] to [PhoneConnectionService].
     * The service will hang up all active connections and reply with a TearDownComplete broadcast.
     */
    fun sendTearDownConnections()

    /**
     * Sends [ServiceAction.ReserveAnswer] with [callId] to [PhoneConnectionService].
     * The service will call [ConnectionManager.reserveAnswer] so the deferred answer is applied
     * when [PhoneConnectionService.onCreateIncomingConnection] fires.
     */
    fun sendReserveAnswer(callId: String)

    /**
     * Sends [ServiceAction.CleanConnections] to [PhoneConnectionService].
     * The service will clear all connections without hanging up individual ones.
     */
    fun sendCleanConnections()

    /**
     * Sends [ServiceAction.SyncAudioState] to [PhoneConnectionService].
     * The service re-emits audio device and mute state for all active connections back to the
     * main process via broadcasts. Called from [ForegroundService.onDelegateSet] to restore
     * Flutter audio UI after hot restart.
     */
    fun sendSyncAudioState()

    /**
     * Sends [ServiceAction.SyncConnectionState] to [PhoneConnectionService].
     * The service re-fires [CallLifecycleEvent.AnswerCall] for every connection whose
     * [PhoneConnection.hasAnswered] flag is true. Called from [ForegroundService.onCreate] so
     * that connections answered before the main process started are reflected in
     * [MainProcessConnectionTracker.connectionStates].
     */
    fun sendSyncConnectionState()

    companion object {
        /**
         * Process-wide singleton. Swap the implementation here to change IPC strategy
         * without touching any call site.
         */
        val instance: CallkeepCore get() = InProcessCallkeepCore.instance
    }
}
