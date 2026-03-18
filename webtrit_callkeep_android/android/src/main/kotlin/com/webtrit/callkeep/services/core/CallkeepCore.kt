package com.webtrit.callkeep.services.core

import android.Manifest
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.models.CallMetadata

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
    fun promote(callId: String, metadata: CallMetadata, state: PCallkeepConnectionState)
    fun markAnswered(callId: String)
    fun markHeld(callId: String, onHold: Boolean)
    fun markTerminated(callId: String)

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

    companion object {
        /**
         * Process-wide singleton. Swap the implementation here to change IPC strategy
         * without touching any call site.
         */
        val instance: CallkeepCore get() = InProcessCallkeepCore.instance
    }
}
