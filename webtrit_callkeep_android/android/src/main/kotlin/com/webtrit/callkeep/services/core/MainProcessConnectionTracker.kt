package com.webtrit.callkeep.services.core

import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepDisconnectCause
import com.webtrit.callkeep.PCallkeepDisconnectCauseType
import com.webtrit.callkeep.models.CallConnectionState
import com.webtrit.callkeep.models.CallMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight shadow registry that mirrors [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]
 * connection state in the main process.
 *
 * Updated from broadcasts emitted by [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]:
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.IncomingConnectionReported] -> promote incoming
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.AnswerCall]           -> markAnswered
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.HungUp] /
 *   [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.DeclineCall]          -> markTerminated
 * - [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.OngoingCall]          -> promote outgoing
 *
 * All per-call state lives in a single [CallRecord] per callId, and every transition is one
 * atomic [ConcurrentHashMap.compute] on that record: a reader always observes a consistent
 * record, never a half-applied transition, and different calls never block each other. The
 * record's fields deliberately mirror the independent facts the tracker has always kept —
 * a call CAN be both promoted and pending for a moment (a push-path re-registration of a call
 * the app already knows), so registration and pending are separate fields, not one exclusive
 * phase.
 *
 * Termination is derived: a call is considered terminated when its state was observed at least
 * once ([CallRecord.state] is set) and it is no longer registered, pending, answered, or
 * awaiting a deferred answer. No explicit terminated flag is maintained, so a call that
 * re-arrives with the same ID (e.g. transfer back) is never incorrectly blocked.
 *
 * This allows [ForegroundService] and [com.webtrit.callkeep.ConnectionsApi] to query connection
 * state without crossing a process boundary. The main process never reads
 * [com.webtrit.callkeep.services.services.connection.PhoneConnectionService.connectionManager]
 * directly — that object lives in the `:callkeep_core` JVM and is empty in the main process.
 * Call state is mirrored via a combination of main-process updates (pending registration and
 * local guards) and IPC broadcasts from `:callkeep_core` for lifecycle transitions.
 */
class MainProcessConnectionTracker internal constructor() : ConnectionTracker {
    // callId -> the one record holding all per-call state (see CallRecord for field semantics).
    private val calls = ConcurrentHashMap<String, CallRecord>()

    // Runs [transform] as one atomic per-callId transition, creating an empty record first if
    // none exists. Every write goes through here (or computeIfPresent for absent-is-no-op ops).
    private inline fun transition(
        callId: String,
        crossinline transform: (CallRecord) -> CallRecord,
    ) {
        calls.compute(callId) { _, rec -> transform(rec ?: CallRecord()) }
    }

    // -------------------------------------------------------------------------
    // Write operations — called from ForegroundService broadcast receiver
    // -------------------------------------------------------------------------

    /**
     * Register a call that has been sent to Telecom but whose [com.webtrit.callkeep.services.services.connection.PhoneConnection]
     * has not yet been created (i.e., between addNewIncomingCall / startOutgoingCall and
     * onCreateIncoming/OutgoingConnection).
     *
     * The call's [CallRecord.metadata] is intentionally NOT set here — only [CallRecord.pending].
     * This keeps [exists] returning false so that [ForegroundService.answerCall] correctly
     * routes to the deferred-answer path ([reserveAnswer]) rather than attempting to answer
     * a PhoneConnection that does not yet exist. Metadata is populated only in [promote].
     *
     * Returns true if [callId] was newly marked pending, false if it was already pending.
     * Callers can use this to determine whether they own the pending entry and should
     * roll it back on error — avoiding a race where a second caller's error removes the first
     * caller's genuine pending entry.
     */
    override fun addPending(callId: String): Boolean {
        var newlyPending = false
        // Reset all per-call lifecycle guards from any prior use of this callId (e.g.
        // transfer-back reusing the same callId). Without this, a reused callId can inherit
        // stale guards from the previous call — for example, a stale endCallDispatched would
        // cause the second clearAndMarkEndCallDispatched to return false, suppressing the
        // required performEndCall. metadata and state are deliberately untouched: state must
        // survive this reset (cold-start adoption reads it), and an already-promoted record
        // stays promoted (push-path re-registration window). endedWithoutFlutterState is
        // sticky — see its declaration.
        calls.compute(callId) { _, rec ->
            val r = rec ?: CallRecord()
            newlyPending = !r.pending
            r.copy(
                pending = true,
                answered = false,
                pendingAnswer = false,
                endCallDispatched = false,
                directNotified = false,
            )
        }
        return newlyPending
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
        // Reset all per-call lifecycle guards in case addPending was not called first (push-path),
        // or in case this callId is being reused without going through addPending. Note this also
        // clears an earlier `answered` — adoption sites must call markAnswered AFTER promote.
        // endedWithoutFlutterState is sticky — see its declaration.
        transition(callId) { rec ->
            rec.copy(
                metadata = metadata,
                pending = false,
                state = state,
                answered = false,
                pendingAnswer = false,
                endCallDispatched = false,
                directNotified = false,
            )
        }
    }

    /**
     * Mark [callId] as answered (lifecycle guard for isAnswered/checkIncomingDuplicate).
     *
     * Does NOT stamp the connection state: the ACTIVE state is mirrored from the real connection via
     * [updateState] (PhoneConnection.onStateChanged for Telecom; explicit ConnectionStateChanged from
     * StandaloneCallService). The initial registration snapshot is still set by [promote].
     */
    override fun markAnswered(callId: String) {
        transition(callId) { it.copy(answered = true) }
    }

    /**
     * Mirror the authoritative connection [state] for [callId]. The source of truth is the real
     * android.telecom.Connection state, broadcast from PhoneConnection.onStateChanged (and emitted
     * explicitly by the no-Telecom StandaloneCallService). Replaces the per-event state stamping that
     * the removed markAnswered(ACTIVE)/markHeld did; like those it writes [CallRecord.state]
     * unconditionally (it is NOT gated on registration), so the state survives an
     * [addPending] reset and the cold-start "already answered" detection in reportNewIncomingCall keeps
     * working. Touches no lifecycle guard. Termination (STATE_DISCONNECTED) is owned by
     * [markTerminated] on the cause-carrying events, not by this mirror.
     */
    override fun updateState(
        callId: String,
        state: CallConnectionState,
    ) {
        // Terminal state is owned by markTerminated (via the cause-carrying HungUp/DeclineCall events),
        // not by this mirror — guard here so a future ConnectionStateChanged(DISCONNECTED) call site
        // cannot accidentally override the termination path.
        if (state == CallConnectionState.DISCONNECTED) return
        transition(callId) { it.copy(state = state.toPCallkeepConnectionState()) }
    }

    // Conversion from the local model enum to the Pigeon enum lives here, at the core boundary,
    // so model/domain code (CallMetadata) stays free of the generated PCallkeepConnectionState.
    private fun CallConnectionState.toPCallkeepConnectionState(): PCallkeepConnectionState =
        when (this) {
            CallConnectionState.INITIALIZING -> PCallkeepConnectionState.STATE_INITIALIZING
            CallConnectionState.NEW -> PCallkeepConnectionState.STATE_NEW
            CallConnectionState.RINGING -> PCallkeepConnectionState.STATE_RINGING
            CallConnectionState.DIALING -> PCallkeepConnectionState.STATE_DIALING
            CallConnectionState.ACTIVE -> PCallkeepConnectionState.STATE_ACTIVE
            CallConnectionState.HOLDING -> PCallkeepConnectionState.STATE_HOLDING
            CallConnectionState.DISCONNECTED -> PCallkeepConnectionState.STATE_DISCONNECTED
        }

    override fun updateMetadata(metadata: CallMetadata) {
        calls.computeIfPresent(metadata.callId) { _, rec ->
            // No-op while not promoted: mid-call merges only apply to a registered call.
            val existing = rec.metadata ?: return@computeIfPresent rec
            rec.copy(metadata = existing.mergeWith(metadata))
        }
    }

    /**
     * Mark [callId] as terminated. Clears the registration, pending and answer facts in one
     * atomic transition so that [isTerminated] returns true (derived: seen and no longer
     * active in any way). The state entry is kept (as STATE_DISCONNECTED) — it is the
     * "ever seen" marker. Callback guards are deliberately untouched.
     */
    override fun markTerminated(callId: String) {
        transition(callId) { rec ->
            rec.copy(
                metadata = null,
                pending = false,
                answered = false,
                pendingAnswer = false,
                state = PCallkeepConnectionState.STATE_DISCONNECTED,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Read operations — replaces PhoneConnectionService.connectionManager.* reads
    // -------------------------------------------------------------------------

    /** Returns true if an active connection record exists for [callId]. */
    override fun exists(callId: String): Boolean = calls[callId]?.metadata != null

    /** Returns true if [callId] is in pending state (Telecom notified, PhoneConnection not yet created). */
    override fun isPending(callId: String): Boolean = calls[callId]?.pending == true

    /** Returns a non-destructive snapshot of all currently pending call IDs. */
    override fun getPendingCallIds(): Set<String> =
        calls.entries.filter { it.value.pending }.mapTo(mutableSetOf()) { it.key }

    /**
     * Returns true if [callId] was previously observed (i.e. its [CallRecord.state] was set
     * via [promote], [updateState] or [markTerminated]) and is no longer registered, pending,
     * answered, or awaiting a deferred answer.
     *
     * Requiring an observed state prevents false positives for callIds that were
     * never tracked: an unknown callId with no active facts is NOT considered terminated —
     * it is simply unknown. Without this guard, [ForegroundService.endCall] would
     * misclassify an unknown callId as terminated and fire a spurious [performEndCall].
     *
     * Termination is still derived — no explicit terminated flag is maintained — so a call
     * that re-arrives with the same ID (e.g. transfer back) is never blocked once it
     * re-enters the pending state via [addPending] or is re-registered via [promote].
     * Unlike the former multi-collection implementation, this reads ONE immutable record,
     * so the answer is always a consistent snapshot.
     */
    override fun isTerminated(callId: String): Boolean {
        val rec = calls[callId] ?: return false
        return rec.state != null &&
            rec.metadata == null &&
            !rec.pending &&
            !rec.pendingAnswer &&
            !rec.answered
    }

    /** Returns true if [callId] has been answered. */
    override fun isAnswered(callId: String): Boolean = calls[callId]?.answered == true

    /** Returns [CallMetadata] for [callId], or null if not tracked. */
    override fun get(callId: String): CallMetadata? = calls[callId]?.metadata

    /** Returns metadata for all active (non-terminated) calls. */
    override fun getAll(): List<CallMetadata> = calls.values.mapNotNull { it.metadata }

    /** Returns the last known Pigeon connection state for [callId], or null if not tracked. */
    override fun getState(callId: String): PCallkeepConnectionState? = calls[callId]?.state

    /**
     * Constructs a [PCallkeepConnection] for [callId] using stored metadata and state.
     * Returns null if [callId] is not currently tracked.
     */
    override fun toPCallkeepConnection(callId: String): PCallkeepConnection? {
        val rec = calls[callId] ?: return null
        val metadata = rec.metadata ?: return null
        val state = rec.state ?: PCallkeepConnectionState.STATE_NEW
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
     * Clear the pending mark for [callId] without touching any other state.
     *
     * Called when [com.webtrit.callkeep.services.services.foreground.ForegroundService.reportNewIncomingCall]
     * receives an error from [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]:
     * the call was never actually registered with Telecom, so the pending entry must be
     * rolled back to prevent [drainUnconnectedPendingCallIds] from firing a spurious
     * performEndCall during the next [com.webtrit.callkeep.services.services.foreground.ForegroundService.tearDown].
     */
    override fun removePending(callId: String) {
        calls.computeIfPresent(callId) { _, rec -> rec.copy(pending = false) }
    }

    /**
     * Reserve a deferred answer for [callId] before its [com.webtrit.callkeep.services.services.connection.PhoneConnection]
     * is created. Mirrors [com.webtrit.callkeep.services.services.connection.ConnectionManager.reserveAnswer].
     */
    override fun reserveAnswer(callId: String) {
        transition(callId) { it.copy(pendingAnswer = true) }
    }

    /**
     * Consume and return whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation; false if none existed.
     */
    override fun consumeAnswer(callId: String): Boolean {
        var hadReservation = false
        calls.computeIfPresent(callId) { _, rec ->
            hadReservation = rec.pendingAnswer
            rec.copy(pendingAnswer = false)
        }
        return hadReservation
    }

    // -------------------------------------------------------------------------
    // tearDown helpers
    // -------------------------------------------------------------------------

    /**
     * Drain all pending call IDs that have not yet been promoted to active connections.
     * Used by [ForegroundService.tearDown] to fire performEndCall for calls that were
     * sent to Telecom but whose PhoneConnection was never created.
     *
     * The drained IDs lose their pending mark; subsequent [isPending] calls return false.
     * Per-callId this is atomic; across callIds it is not (same as the former
     * snapshot-then-clear implementation).
     */
    override fun drainUnconnectedPendingCallIds(): Set<String> {
        val drained = mutableSetOf<String>()
        for (callId in calls.keys) {
            calls.computeIfPresent(callId) { _, rec ->
                if (rec.pending) {
                    drained.add(callId)
                    rec.copy(pending = false)
                } else {
                    rec
                }
            }
        }
        return drained
    }

    /**
     * Clear all tracked state. Called at the end of [ForegroundService.tearDown]
     * after all Flutter notifications and native connection cleanup have been dispatched.
     */
    override fun clear() {
        calls.clear()
    }

    // -------------------------------------------------------------------------
    // Callback guards
    // -------------------------------------------------------------------------

    override fun markDirectNotified(callId: String) {
        transition(callId) { it.copy(directNotified = true) }
    }

    override fun consumeDirectNotified(callId: String): Boolean {
        var hadMark = false
        calls.computeIfPresent(callId) { _, rec ->
            hadMark = rec.directNotified
            rec.copy(directNotified = false)
        }
        return hadMark
    }

    override fun markEndCallDispatched(callId: String): Boolean {
        var newlyMarked = false
        calls.compute(callId) { _, rec ->
            val r = rec ?: CallRecord()
            newlyMarked = !r.endCallDispatched
            r.copy(endCallDispatched = true)
        }
        return newlyMarked
    }

    override fun markEndedWithoutFlutterState(callId: String) {
        transition(callId) { it.copy(endedWithoutFlutterState = true) }
    }

    override fun wasEndedWithoutFlutterState(callId: String): Boolean =
        calls[callId]?.endedWithoutFlutterState == true

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
