package com.webtrit.callkeep.services.services.foreground

import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
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
    fun promote(callId: String, metadata: CallMetadata, state: PCallkeepConnectionState)

    /** Mark [callId] as answered and advance its state to STATE_ACTIVE. */
    fun markAnswered(callId: String)

    /** Update the hold state for [callId] (STATE_HOLDING / STATE_ACTIVE). */
    fun markHeld(callId: String, onHold: Boolean)

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
    // Read operations
    // -------------------------------------------------------------------------

    /** Returns true if an active connection record exists for [callId]. */
    fun exists(callId: String): Boolean

    /** Returns true if [callId] is in pending state. */
    fun isPending(callId: String): Boolean

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
