package com.webtrit.callkeep.services.core

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
 * Termination is derived: a call is considered terminated when it is absent from all active
 * tracking sets ([connections], [pendingCallIds], [pendingAnswers], [answeredCallIds]).
 * No explicit terminated set is maintained, so a call that re-arrives with the same ID
 * (e.g. transfer back) is never incorrectly blocked.
 *
 * This allows [ForegroundService] and [com.webtrit.callkeep.ConnectionsApi] to query connection
 * state without crossing a process boundary. The main process never reads
 * [com.webtrit.callkeep.services.services.connection.PhoneConnectionService.connectionManager]
 * directly — that object lives in the `:callkeep_core` JVM and is empty in the main process.
 * Call state is mirrored via a combination of main-process updates (pending registration and
 * local guards) and IPC broadcasts from `:callkeep_core` for lifecycle transitions.
 */
class MainProcessConnectionTracker internal constructor() : ConnectionTracker {
    // callId -> metadata for all known, non-terminated calls
    private val connections = ConcurrentHashMap<String, CallMetadata>()

    // callIds registered with Telecom but whose PhoneConnection has not yet been created
    private val pendingCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds that have been answered by the user
    private val answeredCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds for which answerCall was requested before the PhoneConnection was created
    private val pendingAnswers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // -------------------------------------------------------------------------
    // Callback guards (moved from ForegroundService)
    // -------------------------------------------------------------------------

    // callIds whose termination was directly notified via performEndCall in tearDown().
    // Suppresses the stale async HungUp broadcast that arrives after the new session starts.
    private val directNotifiedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds for which endCall() has already dispatched a HungUpCall IPC or re-fired
    // performEndCall for a Telecom-terminated call. Prevents duplicate performEndCall.
    private val endCallDispatchedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // callIds successfully registered via ForegroundService.reportNewIncomingCall
    // (foreground signaling path). Suppresses the DidPushIncomingCall broadcast that
    // follows via the :callkeep_core IPC round-trip, preventing a duplicate push-path
    // ActiveCall entry alongside the signaling-path entry.
    private val signalingRegisteredCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
     *
     * Returns true if [callId] was newly inserted into the pending set, false if it was already
     * present. Callers can use this to determine whether they own the pending entry and should
     * roll it back on error — avoiding a race where a second caller's error removes the first
     * caller's genuine pending entry.
     */
    override fun addPending(callId: String): Boolean {
        // Reset any stale lifecycle state from a prior use of this callId in the same session.
        answeredCallIds.remove(callId)
        pendingAnswers.remove(callId)
        return pendingCallIds.add(callId)
    }

    /**
     * Promote a pending call to a fully registered connection once the
     * [com.webtrit.callkeep.services.services.connection.PhoneConnection] has been created.
     *
     * @param state the initial Telecom state reported for this call.
     *   Use [PCallkeepConnectionState.STATE_RINGING] for incoming, [PCallkeepConnectionState.STATE_DIALING] for outgoing.
     */
    override fun promote(
        callId: String,
        metadata: CallMetadata,
        state: PCallkeepConnectionState,
    ) {
        // Reset stale lifecycle sets in case addPending was not called first (push-path),
        // or in case this callId was reused without going through addPending.
        answeredCallIds.remove(callId)
        pendingAnswers.remove(callId)
        connections[callId] = metadata
        pendingCallIds.remove(callId)
        connectionStates[callId] = state
    }

    /**
     * Mark [callId] as answered and advance its state to [PCallkeepConnectionState.STATE_ACTIVE].
     */
    override fun markAnswered(callId: String) {
        answeredCallIds.add(callId)
        connectionStates[callId] = PCallkeepConnectionState.STATE_ACTIVE
    }

    /**
     * Update the hold state for [callId].
     * Advances its state to [PCallkeepConnectionState.STATE_HOLDING] when [onHold] is true,
     * or back to [PCallkeepConnectionState.STATE_ACTIVE] when false.
     * This keeps [getConnections] in sync with the Telecom hold state so that callers never
     * see a stale ACTIVE state for a held call.
     */
    override fun markHeld(
        callId: String,
        onHold: Boolean,
    ) {
        connectionStates[callId] =
            if (onHold) {
                PCallkeepConnectionState.STATE_HOLDING
            } else {
                PCallkeepConnectionState.STATE_ACTIVE
            }
    }

    /**
     * Mark [callId] as terminated. Removes it from all active tracking sets so that
     * [isTerminated] returns true (derived: absent from all sets = terminated).
     */
    override fun markTerminated(callId: String) {
        connections.remove(callId)
        answeredCallIds.remove(callId)
        pendingCallIds.remove(callId)
        pendingAnswers.remove(callId)
        connectionStates[callId] = PCallkeepConnectionState.STATE_DISCONNECTED
    }

    // -------------------------------------------------------------------------
    // Read operations — replaces PhoneConnectionService.connectionManager.* reads
    // -------------------------------------------------------------------------

    /** Returns true if an active connection record exists for [callId]. */
    override fun exists(callId: String): Boolean = connections.containsKey(callId)

    /** Returns true if [callId] is in pending state (Telecom notified, PhoneConnection not yet created). */
    override fun isPending(callId: String): Boolean = pendingCallIds.contains(callId)

    /** Returns a non-destructive snapshot of all currently pending call IDs. */
    override fun getPendingCallIds(): Set<String> = pendingCallIds.toSet()

    /**
     * Returns true if [callId] was previously observed (i.e. appeared in [connectionStates]
     * via [addPending] → [markTerminated], [promote], or [markAnswered]) and is no longer
     * present in any active tracking set.
     *
     * Requiring [connectionStates] presence prevents false positives for callIds that were
     * never tracked: an unknown callId absent from all sets is NOT considered terminated —
     * it is simply unknown. Without this guard, [ForegroundService.endCall] would
     * misclassify an unknown callId as terminated and fire a spurious [performEndCall].
     *
     * Termination is still derived — no explicit terminated set is maintained — so a call
     * that re-arrives with the same ID (e.g. transfer back) is never blocked once it
     * re-enters [pendingCallIds] via [addPending] or [promote].
     */
    override fun isTerminated(callId: String): Boolean =
        connectionStates.containsKey(callId) &&
            !connections.containsKey(callId) &&
            !pendingCallIds.contains(callId) &&
            !pendingAnswers.contains(callId) &&
            !answeredCallIds.contains(callId)

    /** Returns true if [callId] has been answered. */
    override fun isAnswered(callId: String): Boolean = answeredCallIds.contains(callId)

    /** Returns [CallMetadata] for [callId], or null if not tracked. */
    override fun get(callId: String): CallMetadata? = connections[callId]

    /** Returns metadata for all active (non-terminated) calls. */
    override fun getAll(): List<CallMetadata> = connections.values.toList()

    /** Returns the last known Pigeon connection state for [callId], or null if not tracked. */
    override fun getState(callId: String): PCallkeepConnectionState? = connectionStates[callId]

    /**
     * Constructs a [PCallkeepConnection] for [callId] using stored metadata and state.
     * Returns null if [callId] is not currently tracked.
     */
    override fun toPCallkeepConnection(callId: String): PCallkeepConnection? {
        val metadata = connections[callId] ?: return null
        val state = connectionStates[callId] ?: PCallkeepConnectionState.STATE_NEW
        val disconnectCause =
            PCallkeepDisconnectCause(
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
    override fun removePending(callId: String) {
        pendingCallIds.remove(callId)
    }

    /**
     * Reserve a deferred answer for [callId] before its [com.webtrit.callkeep.services.services.connection.PhoneConnection]
     * is created. Mirrors [com.webtrit.callkeep.services.services.connection.ConnectionManager.reserveAnswer].
     */
    override fun reserveAnswer(callId: String) {
        pendingAnswers.add(callId)
    }

    /**
     * Consume and return whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation; false if none existed.
     */
    override fun consumeAnswer(callId: String): Boolean = pendingAnswers.remove(callId)

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
    override fun drainUnconnectedPendingCallIds(): Set<String> {
        val unconnected = pendingCallIds.toSet()
        pendingCallIds.clear()
        return unconnected
    }

    /**
     * Clear all tracked state. Called at the end of [ForegroundService.tearDown]
     * after all Flutter notifications and native connection cleanup have been dispatched.
     */
    override fun clear() {
        connections.clear()
        pendingCallIds.clear()
        answeredCallIds.clear()
        pendingAnswers.clear()
        connectionStates.clear()
        directNotifiedCallIds.clear()
        endCallDispatchedCallIds.clear()
        signalingRegisteredCallIds.clear()
    }

    // -------------------------------------------------------------------------
    // Callback guards
    // -------------------------------------------------------------------------

    override fun markDirectNotified(callId: String) {
        directNotifiedCallIds.add(callId)
    }

    override fun consumeDirectNotified(callId: String): Boolean = directNotifiedCallIds.remove(callId)

    override fun markEndCallDispatched(callId: String): Boolean = endCallDispatchedCallIds.add(callId)

    override fun markSignalingRegistered(callId: String) {
        signalingRegisteredCallIds.add(callId)
    }

    override fun consumeSignalingRegistered(callId: String): Boolean = signalingRegisteredCallIds.remove(callId)

    companion object {
        /**
         * Process-wide singleton. All main-process components ([ForegroundService],
         * [com.webtrit.callkeep.ConnectionsApi], etc.) share this single instance so that
         * connection state is consistent across the process.
         *
         * Typed as [ConnectionTracker] so that the implementation can be swapped
         * (e.g. for a broadcast-backed variant after the `:callkeep_core` process split)
         * without touching any caller.
         */
        val instance: ConnectionTracker = MainProcessConnectionTracker()
    }
}
