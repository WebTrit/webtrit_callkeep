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
import com.webtrit.callkeep.models.CallConnectionState
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
     * Returns null if [callId] is free and a new incoming call may proceed.
     * Returns [PIncomingCallError] with [PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED]
     * if already answered, or [PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS] if still ringing/active.
     */
    fun checkIncomingDuplicate(callId: String): PIncomingCallError?

    /**
     * Returns the action to take for an answer request on [callId]:
     * [AnswerCallRoute.AnswerImmediately] if a live connection exists,
     * [AnswerCallRoute.DeferAnswer] if pending but not yet connected,
     * [AnswerCallRoute.NotFound] if the callId is unknown.
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

    /**
     * Mirror the authoritative connection [state] for [callId] (source of truth = the real
     * android.telecom.Connection state via PhoneConnection.onStateChanged, or the StandaloneCallService
     * transitions). Writes state UNCONDITIONALLY — it does NOT register the call and may be called
     * before promote (state persists across addPending, which the cold-start adoption relies on).
     * Ignores terminal DISCONNECTED (owned by [markTerminated]).
     */
    fun updateState(
        callId: String,
        state: CallConnectionState,
    )

    fun markTerminated(callId: String)

    /**
     * Clears all state for [callId] and records that [performEndCall] has been dispatched.
     *
     * Returns true if this is the first dispatch, false if already dispatched.
     */
    fun clearAndMarkEndCallDispatched(callId: String): Boolean

    /** Reserves a deferred answer for [callId] so it is applied when the connection is created. */
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

    /**
     * Mark [callId] as having been reported by the host app via `reportNewIncomingCall`
     * (the app/signaling path), as opposed to discovered by callkeep through the push/Telecom path.
     *
     * Purpose: de-duplicate the incoming-call notification to Flutter. When the app itself reported
     * the call, Flutter already knows about it (`__onCallSignalingEventIncoming`). The
     * `IncomingConnectionReported` broadcast that still arrives afterwards via the `:callkeep_core` IPC
     * round-trip is then suppressed (see [consumeReportedIncoming]) so it does not add a second,
     * push-path ActiveCall (line -1) alongside the existing app-path entry (line 0) for the same callId.
     */
    fun markReportedIncoming(callId: String)

    /**
     * Returns true and clears the mark if [callId] was app-reported (see [markReportedIncoming]).
     *
     * Consume-on-read: the guard fires at most once per call, so the first `IncomingConnectionReported`
     * for an app-reported call is suppressed and any subsequent delivery (e.g. an explicit
     * re-emit to a newly attached delegate) is no longer affected.
     */
    fun consumeReportedIncoming(callId: String): Boolean

    // -------------------------------------------------------------------------
    // Connection event receivers
    // -------------------------------------------------------------------------

    /**
     * Subscribes [listener] to receive connection events. Callers must balance every
     * [addConnectionEventListener] with a corresponding [removeConnectionEventListener].
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

    /**
     * Reserves [metadata]'s callId in the pending tracker, then dispatches to the active
     * call backend (Telecom or standalone).
     *
     * Failure modes:
     * - **Logical errors** are delivered via [onError] with a [PIncomingCallError]. The
     *   pending reservation is drained before [onError] is invoked.
     * - **Synchronous exceptions** from the backend (e.g. uninitialized ContextHolder)
     *   propagate to the caller after the pending reservation is drained. The original
     *   throwable reaches the Pigeon channel as channel-error, so its message and stack
     *   trace are preserved for Dart-side diagnostics.
     *
     * Callers that pre-register state (Pigeon callbacks, timeouts) before invoking this
     * method must either: (a) wrap the call in their own try/catch and clean that state
     * on throw, or (b) rely on a self-cleaning safety-net (e.g. a deferred timeout that
     * removes the stale entries) — exception propagation will skip [onError] entirely
     * in the synchronous-throw case.
     */
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
     * Asks [PhoneConnectionService] to REPLAY the current audio state (device + mute) for all
     * active connections back to the main process via broadcasts -- a one-way pull, not a two-way
     * sync. Called from [ForegroundService.onDelegateSet] to restore the Flutter audio UI once a
     * freshly attached delegate is ready (cold start / hot restart / warm re-attach). The sibling
     * of [replayConnectionStates].
     */
    fun replayAudioState()

    /**
     * Asks [PhoneConnectionService] to REPLAY the current connection lifecycle back to the main
     * process, so a freshly (re)attached delegate is seeded with state it would otherwise have
     * missed. This is a one-way pull (main -> `:callkeep_core` -> re-fired broadcasts), NOT a
     * two-way sync: the live `:callkeep_core` connections are the source of truth and simply
     * re-announce themselves. The service re-fires [CallLifecycleEvent.AnswerCall] for every
     * connection whose [PhoneConnection.hasAnswered] flag is true. Called from
     * [ForegroundService.onCreate] so that connections answered before the main process started
     * are reflected in [MainProcessConnectionTracker.connectionStates] (cold-start race).
     */
    fun replayConnectionStates()

    companion object {
        /**
         * Process-wide singleton. Swap the implementation here to change IPC strategy
         * without touching any call site.
         */
        val instance: CallkeepCore get() = InProcessCallkeepCore.instance
    }
}
