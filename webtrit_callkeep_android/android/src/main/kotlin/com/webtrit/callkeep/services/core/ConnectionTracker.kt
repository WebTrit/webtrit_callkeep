package com.webtrit.callkeep.services.core

import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.models.CallConnectionState
import com.webtrit.callkeep.models.CallMetadata

/**
 * Read/write interface for the main-process shadow of [com.webtrit.callkeep.services.services.connection.PhoneConnectionService]
 * connection state.
 *
 * The concrete implementation is [MainProcessConnectionTracker]. After the `:callkeep_core`
 * process split (PR-9b), a broadcast-backed implementation can be substituted here without
 * touching any caller.
 */
interface ConnectionTracker {
    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Register a call as pending (Telecom notified, PhoneConnection not yet created).
     * Returns true if newly inserted, false if already present.
     */
    fun addPending(callId: String): Boolean

    /**
     * Promote a pending call to a fully registered connection once the PhoneConnection exists.
     */
    fun promote(
        callId: String,
        metadata: CallMetadata,
        state: PCallkeepConnectionState,
    )

    /**
     * Mark [callId] as answered (lifecycle guard for isAnswered / checkIncomingDuplicate).
     * Does NOT stamp the connection state — ACTIVE is mirrored via [updateState].
     */
    fun markAnswered(callId: String)

    /**
     * Mirror the authoritative connection [state] for [callId]. Source of truth is the real
     * android.telecom.Connection state (PhoneConnection.onStateChanged) / the StandaloneCallService
     * transitions. Writes the state UNCONDITIONALLY (it does NOT register the call and is not gated on
     * connections membership): state may be set before [promote] and is preserved across an [addPending]
     * reset, which the cold-start "already answered" detection relies on. Touches no guard set. Ignores
     * terminal DISCONNECTED — that is owned by [markTerminated] via the cause-carrying events.
     */
    fun updateState(
        callId: String,
        state: CallConnectionState,
    )

    /**
     * Merge [metadata] into the stored record for [metadata.callId].
     * No-op if the call is not yet promoted to connections (still pending).
     * Used to propagate mid-call updates (e.g. hasVideo toggle) to the shadow
     * without going through a full promote() cycle.
     */
    fun updateMetadata(metadata: CallMetadata) {}

    /** Mark [callId] as terminated, removing it from the active connections map. */
    fun markTerminated(callId: String)

    /** Remove [callId] from the pending set without touching any other state. */
    fun removePending(callId: String)

    /** Reserve a deferred answer for [callId] before its PhoneConnection is created. */
    fun reserveAnswer(callId: String)

    /**
     * Consume and return whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation; false if none existed.
     */
    fun consumeAnswer(callId: String): Boolean

    /**
     * Drain all pending call IDs that have not yet been promoted to active connections.
     * The drained IDs are removed from tracking.
     */
    fun drainUnconnectedPendingCallIds(): Set<String>

    /** Clear all tracked state. */
    fun clear()

    // -------------------------------------------------------------------------
    // Callback guards (moved from ForegroundService)
    // These track which Pigeon callbacks have already been dispatched so that
    // duplicate or stale events are suppressed without per-field clear() calls.
    // -------------------------------------------------------------------------

    /**
     * Mark [callId] as directly notified via performEndCall inside tearDown().
     * Suppresses the subsequent stale async HungUp broadcast that would otherwise
     * fire performEndCall a second time on the new session's delegate.
     */
    fun markDirectNotified(callId: String)

    /**
     * Returns true and removes the mark if [callId] was directly notified.
     * Consuming the mark on first read prevents repeated suppression across sessions.
     */
    fun consumeDirectNotified(callId: String): Boolean

    /**
     * Mark [callId] as having had endCall() dispatched (HungUpCall IPC sent, or
     * performEndCall re-fired for a Telecom-terminated call). Prevents a second
     * explicit endCall() from re-firing performEndCall.
     * Returns true if newly marked, false if already present.
     */
    fun markEndCallDispatched(callId: String): Boolean

    /**
     * Mark [callId] as having been reported by the host app via
     * ForegroundService.reportNewIncomingCall. Suppresses the IncomingConnectionReported broadcast
     * that follows via the :callkeep_core IPC round-trip, preventing a duplicate
     * push-path ActiveCall entry in the app's call state.
     */
    fun markReportedIncoming(callId: String)

    /**
     * Returns true and removes the mark if [callId] was app-reported (see [markReportedIncoming]).
     * Consuming on first read ensures the guard fires at most once per call.
     */
    fun consumeReportedIncoming(callId: String): Boolean

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /** Returns true if an active connection record exists for [callId]. */
    fun exists(callId: String): Boolean

    /** Returns true if [callId] is in pending state. */
    fun isPending(callId: String): Boolean

    /**
     * Returns a snapshot of all call IDs currently in pending state
     * (registered with Telecom, PhoneConnection not yet created).
     *
     * Non-destructive — unlike [drainUnconnectedPendingCallIds], this does not remove
     * any entries. Use this for read-only checks that must account for the broadcast-lag
     * window between PhoneConnection creation in CS and the [promote] call in the tracker.
     */
    fun getPendingCallIds(): Set<String>

    /** Returns true if [callId] has been marked terminated. */
    fun isTerminated(callId: String): Boolean

    /** Returns true if [callId] has been answered. */
    fun isAnswered(callId: String): Boolean

    /** Returns [CallMetadata] for [callId], or null if not tracked. */
    fun get(callId: String): CallMetadata?

    /** Returns metadata for all active (non-terminated) calls. */
    fun getAll(): List<CallMetadata>

    /** Returns the last known Pigeon connection state for [callId], or null if not tracked. */
    fun getState(callId: String): PCallkeepConnectionState?

    /**
     * Constructs a [PCallkeepConnection] for [callId] using stored metadata and state.
     * Returns null if [callId] is not currently tracked.
     */
    fun toPCallkeepConnection(callId: String): PCallkeepConnection?
}
