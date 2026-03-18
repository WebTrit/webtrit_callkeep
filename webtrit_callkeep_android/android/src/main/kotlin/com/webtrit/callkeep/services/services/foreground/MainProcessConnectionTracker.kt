package com.webtrit.callkeep.services.services.foreground

import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepDisconnectCause
import com.webtrit.callkeep.PCallkeepDisconnectCauseType
import com.webtrit.callkeep.models.CallMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight shadow registry that mirrors [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]
 * connection state in the main process.
 *
 * Updated from broadcasts emitted by [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]:
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.DidPushIncomingCall] -> promote incoming
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.AnswerCall]           -> markAnswered
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.HungUp] /
 *   [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.DeclineCall]          -> markTerminated
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.OngoingCall]          -> promote outgoing
 *
 * This allows [ForegroundService] and [com.webtrit.callkeep.ConnectionsApi] to query connection
 * state without crossing a process boundary. In the current single-process setup the tracker
 * avoids tightly coupling to [com.webtrit.callkeep.services.services.connection.PhoneConnectionService.connectionManager].
 * When the `:callkeep_core` process split lands (PR-9b), only the broadcast wiring needs to
 * change — all callers of this tracker remain unchanged.
 */
class MainProcessConnectionTracker {

    // callId -> metadata for all known, non-terminated calls
    private val connections = ConcurrentHashMap<String, CallMetadata>()

    // callIds registered with Telecom but whose PhoneConnection has not yet been created
    private val pendingCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds that have been answered by the user
    private val answeredCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds for which the call has fully terminated (guards duplicate endCall)
    private val terminatedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds for which answerCall was requested before the PhoneConnection was created
    private val pendingAnswers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // last known Pigeon connection state per callId, kept for getConnections() queries
    private val connectionStates = ConcurrentHashMap<String, PCallkeepConnectionState>()

    // -------------------------------------------------------------------------
    // Write operations — called from ForegroundService broadcast receiver
    // -------------------------------------------------------------------------

    /**
     * Register a call that has been sent to Telecom but whose [com.webtrit.callkeep.services.services.connection.PhoneConnection]
     * has not yet been created (i.e., between addNewIncomingCall / startOutgoingCall and
     * onCreateIncoming/OutgoingConnection).
     *
     * The call is intentionally NOT added to [connections] here — only to [pendingCallIds].
     * This keeps [exists] returning false so that [ForegroundService.answerCall] correctly
     * routes to the deferred-answer path ([reserveAnswer]) rather than attempting to answer
     * a PhoneConnection that does not yet exist. [connections] is populated only in [promote].
     */
    fun addPending(callId: String, metadata: CallMetadata) {
        pendingCallIds.add(callId)
    }

    /**
     * Promote a pending call to a fully registered connection once the
     * [com.webtrit.callkeep.services.services.connection.PhoneConnection] has been created.
     *
     * @param state the initial Telecom state reported for this call.
     *   Use [PCallkeepConnectionState.STATE_RINGING] for incoming, [PCallkeepConnectionState.STATE_DIALING] for outgoing.
     */
    fun promote(callId: String, metadata: CallMetadata, state: PCallkeepConnectionState) {
        connections[callId] = metadata
        pendingCallIds.remove(callId)
        connectionStates[callId] = state
    }

    /**
     * Mark [callId] as answered and advance its state to [PCallkeepConnectionState.STATE_ACTIVE].
     */
    fun markAnswered(callId: String) {
        answeredCallIds.add(callId)
        connectionStates[callId] = PCallkeepConnectionState.STATE_ACTIVE
    }

    /**
     * Mark [callId] as terminated. Removes it from the active connections map so that
     * subsequent [exists] / [getAll] calls exclude it, and records it in [terminatedCallIds]
     * so that [isTerminated] returns true for duplicate endCall guards.
     */
    fun markTerminated(callId: String) {
        terminatedCallIds.add(callId)
        connections.remove(callId)
        answeredCallIds.remove(callId)
        pendingCallIds.remove(callId)
        connectionStates[callId] = PCallkeepConnectionState.STATE_DISCONNECTED
    }

    // -------------------------------------------------------------------------
    // Read operations — replaces PhoneConnectionService.connectionManager.* reads
    // -------------------------------------------------------------------------

    /** Returns true if an active connection record exists for [callId]. */
    fun exists(callId: String): Boolean = connections.containsKey(callId)

    /** Returns true if [callId] is in pending state (Telecom notified, PhoneConnection not yet created). */
    fun isPending(callId: String): Boolean = pendingCallIds.contains(callId)

    /** Returns true if [callId] has been marked terminated. */
    fun isTerminated(callId: String): Boolean = terminatedCallIds.contains(callId)

    /** Returns true if [callId] has been answered. */
    fun isAnswered(callId: String): Boolean = answeredCallIds.contains(callId)

    /** Returns [CallMetadata] for [callId], or null if not tracked. */
    fun get(callId: String): CallMetadata? = connections[callId]

    /** Returns metadata for all active (non-terminated) calls. */
    fun getAll(): List<CallMetadata> = connections.values.toList()

    /** Returns the last known Pigeon connection state for [callId], or null if not tracked. */
    fun getState(callId: String): PCallkeepConnectionState? = connectionStates[callId]

    /**
     * Constructs a [PCallkeepConnection] for [callId] using stored metadata and state.
     * Returns null if [callId] is not currently tracked.
     */
    fun toPCallkeepConnection(callId: String): PCallkeepConnection? {
        val metadata = connections[callId] ?: return null
        val state = connectionStates[callId] ?: PCallkeepConnectionState.STATE_NEW
        val disconnectCause = PCallkeepDisconnectCause(
            type = PCallkeepDisconnectCauseType.UNKNOWN,
            reason = "Unknown reason",
        )
        return PCallkeepConnection(callId = metadata.callId, state = state, disconnectCause = disconnectCause)
    }

    // -------------------------------------------------------------------------
    // Deferred answer (mirrors ConnectionManager.reserveAnswer / consumeAnswer)
    // -------------------------------------------------------------------------

    /**
     * Remove [callId] from the pending set without touching any other state.
     *
     * Called when [com.webtrit.callkeep.services.services.foreground.ForegroundService.reportNewIncomingCall]
     * receives an error from [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]:
     * the call was never actually registered with Telecom, so the pending entry must be
     * rolled back to prevent [drainUnconnectedPendingCallIds] from firing a spurious
     * performEndCall during the next [com.webtrit.callkeep.services.services.foreground.ForegroundService.tearDown].
     */
    fun removePending(callId: String) {
        pendingCallIds.remove(callId)
    }

    /**
     * Reserve a deferred answer for [callId] before its [com.webtrit.callkeep.services.services.connection.PhoneConnection]
     * is created. Mirrors [com.webtrit.callkeep.services.services.connection.ConnectionManager.reserveAnswer].
     */
    fun reserveAnswer(callId: String) {
        pendingAnswers.add(callId)
    }

    /**
     * Consume and return whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation; false if none existed.
     */
    fun consumeAnswer(callId: String): Boolean = pendingAnswers.remove(callId)

    // -------------------------------------------------------------------------
    // tearDown helpers
    // -------------------------------------------------------------------------

    /**
     * Drain all pending call IDs that have not yet been promoted to active connections.
     * Used by [ForegroundService.tearDown] to fire performEndCall for calls that were
     * sent to Telecom but whose PhoneConnection was never created.
     *
     * The drained IDs are removed from tracking; subsequent [isPending] calls return false.
     */
    fun drainUnconnectedPendingCallIds(): Set<String> {
        val unconnected = pendingCallIds.toSet()
        pendingCallIds.clear()
        return unconnected
    }

    /**
     * Clear all tracked state. Called at the end of [ForegroundService.tearDown]
     * after all Flutter notifications and native connection cleanup have been dispatched.
     */
    fun clear() {
        connections.clear()
        pendingCallIds.clear()
        answeredCallIds.clear()
        terminatedCallIds.clear()
        pendingAnswers.clear()
        connectionStates.clear()
    }
}
