package com.webtrit.callkeep.services.services.connection

import android.telecom.Connection
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.models.CallMetadata
import java.util.concurrent.ConcurrentHashMap

class ConnectionManager {
    private val connections: ConcurrentHashMap<String, PhoneConnection> = ConcurrentHashMap()
    private val connectionResourceLock = Any()

    // Call IDs sent to Telecom but not yet registered via onCreateIncomingConnection.
    // Guards the async gap between addNewIncomingCall() and connection creation.
    private val pendingCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Call IDs for which a HungUp has been dispatched via any path (connection terminated
    // or ConnectionNotFound). Used by isConnectionDisconnected() for reliable detection
    // even when no PhoneConnection object exists (e.g. pending-but-not-yet-connected calls).
    private val terminatedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Call IDs for which answerCall was requested before onCreateIncomingConnection fired.
    // Consumed in onCreateIncomingConnection to apply the deferred answer immediately
    // after the PhoneConnection is created, closing the async gap between
    // reportNewIncomingCall (which returns as soon as addNewIncomingCall is sent to Telecom)
    // and the binder-thread onCreateIncomingConnection callback.
    private val pendingAnswers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Call IDs that were in pendingCallIds when cleanConnections() was last called.
    // Any subsequent onCreateIncomingConnection for these IDs is a stale Telecom callback
    // arriving after a tearDown and should be rejected to prevent zombie connections.
    // Populated by cleanConnections() and drained by addPendingForIncomingCall() (when
    // the same callId is legitimately re-reported in a new session, which is impossible
    // for UUID call IDs but handled for correctness).
    private val forcedTerminatedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Atomically validates that a call ID can be added and reserves it as pending.
     *
     * Returns null on success (call ID was free and is now reserved).
     * Returns an error enum if the call ID is already pending, disconnected, or active.
     *
     * Using a single lock for check+reserve prevents a race where two concurrent
     * reportNewIncomingCall calls both see isPending=false and both proceed.
     */
    fun checkAndReservePending(callId: String): PIncomingCallErrorEnum? {
        synchronized(connectionResourceLock) {
            val snapshot = connections.entries.joinToString { (id, c) -> "$id:state=${c.state}" }
            android.util.Log.i("CK-ConnectionManager", "checkAndReservePending: callId=$callId pending=$pendingCallIds connections=[$snapshot]")

            return when {
                pendingCallIds.contains(callId) -> {
                    android.util.Log.w("CK-ConnectionManager", "checkAndReservePending: $callId → CALL_ID_ALREADY_EXISTS (in pending)")
                    PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS
                }

                connections[callId]?.state == Connection.STATE_DISCONNECTED -> {
                    android.util.Log.w("CK-ConnectionManager", "checkAndReservePending: $callId → CALL_ID_ALREADY_TERMINATED (STATE_DISCONNECTED in :callkeep_core)")
                    PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED
                }

                connections.containsKey(callId) -> {
                    val answered = connections[callId]?.hasAnswered == true
                    android.util.Log.w("CK-ConnectionManager", "checkAndReservePending: $callId → ${if (answered) "CALL_ID_ALREADY_EXISTS_AND_ANSWERED" else "CALL_ID_ALREADY_EXISTS"} (active in :callkeep_core)")
                    if (answered) {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED
                    } else {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS
                    }
                }

                else -> {
                    pendingCallIds.add(callId)
                    android.util.Log.i("CK-ConnectionManager", "checkAndReservePending: $callId → reserved as pending (free slot)")
                    null
                }
            }
        }
    }

    fun removePending(callId: String) {
        pendingCallIds.remove(callId)
    }

    /**
     * Adds [callId] to [pendingCallIds] in response to a [ServiceAction.NotifyPending] IPC
     * intent from the main process.
     *
     * In the dual-process architecture, [checkAndReservePending] runs in the main-process JVM
     * and populates the main process's [ConnectionManager] instance. The :callkeep_core process
     * has its own instance whose [pendingCallIds] is never touched by the main process.
     * This method bridges the gap: the main process sends a [ServiceAction.NotifyPending] intent
     * just before [TelephonyUtils.addNewIncomingCall], and [PhoneConnectionService.handleNotifyPending]
     * calls this method, ensuring [isPending] returns true when [onCreateIncomingConnection] fires.
     *
     * Also removes [callId] from [forcedTerminatedCallIds] in the (theoretically impossible for
     * UUID callIds) case where a new session re-uses the same ID.
     */
    fun addPendingForIncomingCall(callId: String) {
        forcedTerminatedCallIds.remove(callId)
        pendingCallIds.add(callId)
    }

    /**
     * Returns true if [callId] was in [pendingCallIds] when [cleanConnections] was last called.
     *
     * Used by [onCreateIncomingConnection] to reject stale Telecom callbacks that arrive after
     * a tearDown cleared the pending set. Without this guard, a call that was sent to Telecom
     * via [addNewIncomingCall] but not yet delivered via [onCreateIncomingConnection] before
     * tearDown would create a zombie [PhoneConnection] in the next session.
     */
    fun isForcedTerminated(callId: String): Boolean = forcedTerminatedCallIds.contains(callId)

    /**
     * Returns true if the call ID has been reserved as pending
     * (i.e., addNewIncomingCall was sent to Telecom but onCreateIncomingConnection
     * has not yet fired, or the call was registered and still awaiting full creation).
     */
    fun isPending(callId: String): Boolean {
        synchronized(connectionResourceLock) {
            return pendingCallIds.contains(callId)
        }
    }

    /**
     * Removes and returns all pending call IDs that do NOT yet have a corresponding
     * connection entry. Used by tearDown to fire performEndCall for calls that were
     * reported to Telecom but whose onCreateIncomingConnection has not fired yet.
     */
    fun drainUnconnectedPendingCallIds(): Set<String> {
        synchronized(connectionResourceLock) {
            val unconnected = pendingCallIds.filter { !connections.containsKey(it) }.toSet()
            pendingCallIds.removeAll(unconnected)
            return unconnected
        }
    }

    internal fun addConnection(
        callId: String,
        connection: PhoneConnection,
    ) {
        synchronized(connectionResourceLock) {
            val existing = connections[callId]
            if (existing == null || existing.state == Connection.STATE_DISCONNECTED) {
                connections[callId] = connection
            }
        }
    }

    /**
     * Get a connection by ID.
     */
    fun getConnection(callId: String): PhoneConnection? {
        synchronized(connectionResourceLock) {
            return connections[callId]
        }
    }

    /**
     * Get all connections.
     */
    fun getConnections(): List<PhoneConnection> =
        synchronized(connectionResourceLock) {
            connections.values.filter { it.state != Connection.STATE_DISCONNECTED }
        }

    /**
     * Check if a live (non-disconnected) connection already exists for [callId].
     *
     * A STATE_DISCONNECTED connection is not considered "existing" because it is
     * a terminal object left in the map until tearDown. Treating it as live would
     * block a new incoming call that reuses the same callId (e.g. blind transfer-back).
     */
    fun isConnectionAlreadyExists(callId: String): Boolean {
        synchronized(connectionResourceLock) {
            val existing = connections[callId] ?: return false
            return existing.state != Connection.STATE_DISCONNECTED
        }
    }

    /**
     * Check if available video connections.
     */
    fun hasVideoConnections(): Boolean {
        synchronized(connectionResourceLock) {
            return connections.any { it.value.hasVideo }
        }
    }

    /**
     * Marks a call ID as having had HungUp dispatched, so that a subsequent endCall
     * for the same ID can be detected as a duplicate and rejected with an error.
     * Called when ConnectionNotFound fires for a callId that has no connection object.
     */
    fun markTerminated(callId: String) {
        terminatedCallIds.add(callId)
    }

    /**
     * Records that answerCall was requested for [callId] before its PhoneConnection
     * was created (i.e., before onCreateIncomingConnection fired). The deferred answer
     * is consumed and applied inside onCreateIncomingConnection.
     *
     * Prefer [reserveOrGetConnectionToAnswer] over calling this method directly — it is
     * atomic and eliminates the TOCTOU race with [addConnectionAndConsumeAnswer].
     * This method is kept for cleanup paths that need to drain [pendingAnswers]
     * without holding the connection creation lock (e.g., tearDown, ConnectionNotFound).
     */
    fun reserveAnswer(callId: String) {
        pendingAnswers.add(callId)
    }

    /**
     * Consumes and returns whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation if one exists; returns false otherwise.
     *
     * For cleanup paths only (tearDown, connection-not-found, duplicate-connection rejection).
     * The normal answer flow uses [addConnectionAndConsumeAnswer] and
     * [reserveOrGetConnectionToAnswer] to eliminate the TOCTOU race.
     */
    fun consumeAnswer(callId: String): Boolean = pendingAnswers.remove(callId)

    /**
     * Atomically adds [connection] to the connections map and removes any pending answer
     * reservation for [callId] in one synchronized block.
     *
     * Returns true if a deferred answer was pending (and was consumed); false otherwise.
     *
     * This closes the race between [handleReserveAnswer] (main thread) and
     * [onCreateIncomingConnection] (binder thread): both must hold [connectionResourceLock]
     * to read/write the [connections] + [pendingAnswers] pair, so one always sees a
     * consistent snapshot of both collections.
     */
    fun addConnectionAndConsumeAnswer(
        callId: String,
        connection: PhoneConnection,
    ): Boolean {
        synchronized(connectionResourceLock) {
            val existing = connections[callId]
            if (existing == null || existing.state == Connection.STATE_DISCONNECTED) {
                connections[callId] = connection
            }
            return pendingAnswers.remove(callId)
        }
    }

    /**
     * Atomically either returns the existing [PhoneConnection] for immediate answering,
     * or reserves a deferred answer for [callId] when no connection exists yet.
     *
     * - If a connection exists and has not been answered, returns it so the caller can
     *   invoke [PhoneConnection.onAnswer] directly.
     * - If no connection exists, adds [callId] to [pendingAnswers] and returns null.
     *   [onCreateIncomingConnection] will consume the reservation via
     *   [addConnectionAndConsumeAnswer] when it fires.
     * - If a connection exists but was already answered, returns null without re-reserving.
     *
     * This is the counterpart to [addConnectionAndConsumeAnswer]: both are synchronized
     * on [connectionResourceLock], eliminating the TOCTOU gap between checking for a
     * connection and reserving the deferred answer.
     */
    fun reserveOrGetConnectionToAnswer(callId: String): PhoneConnection? {
        synchronized(connectionResourceLock) {
            val connection = connections[callId]
            return if (connection != null && !connection.hasAnswered) {
                connection
            } else {
                if (connection == null) pendingAnswers.add(callId)
                null
            }
        }
    }

    /**
     * Check if a connection is terminated.
     *
     * Checks the [terminatedCallIds] set (for calls terminated via ConnectionNotFound or
     * explicit marking) and the Telecom framework's own [Connection.STATE_DISCONNECTED]
     * as the single source of truth for calls that went through a full connection lifecycle.
     */
    fun isConnectionDisconnected(callId: String): Boolean {
        if (terminatedCallIds.contains(callId)) return true
        synchronized(connectionResourceLock) {
            return connections[callId]?.state == Connection.STATE_DISCONNECTED
        }
    }

    /**
     * Return active connection.
     */
    fun getActiveConnection(): PhoneConnection? {
        synchronized(connectionResourceLock) {
            return connections.values.find { it.state == Connection.STATE_ACTIVE }
        }
    }

    /**
     * Checks whether there is an incoming connection.
     *
     * Incoming connections are in the `STATE_NEW` or `STATE_RINGING` state.
     *
     * @return `true` if there is an incoming connection, `false` otherwise.
     */
    fun isExistsIncomingConnection(): Boolean {
        synchronized(connectionResourceLock) {
            return connections.values.any { it.state == Connection.STATE_NEW || it.state == Connection.STATE_RINGING }
        }
    }

    fun cleanConnections() {
        synchronized(connectionResourceLock) {
            connections.values.forEach { it.destroy() }
            connections.clear()
            // Snapshot current pendingCallIds into forcedTerminated before clearing.
            // Telecom may still fire onCreateIncomingConnection for these IDs after this
            // tearDown; isForcedTerminated() lets that guard reject them as zombie calls.
            forcedTerminatedCallIds.clear()
            forcedTerminatedCallIds.addAll(pendingCallIds)
            pendingCallIds.clear()
            terminatedCallIds.clear()
            pendingAnswers.clear()
        }
    }

    /**
     * Checks whether the connection with the specified ID has been answered.
     *
     * @param id the identifier of the connection to check.
     * @return `true` if the connection has been answered, `false` otherwise.
     */
    fun isConnectionAnswered(id: String): Boolean = connections[id]?.hasAnswered == true

    override fun toString(): String {
        synchronized(connectionResourceLock) {
            val connectionsInfo =
                connections
                    .map { (callId, connection) ->
                        "Call ID: $callId, State: ${connection.state}"
                    }.joinToString(separator = "\n")

            return """
                ConnectionManager {
                    Active Connections:
                    $connectionsInfo
                }
                """.trimIndent()
        }
    }

    companion object {
        fun validateConnectionAddition(
            metadata: CallMetadata,
            onSuccess: () -> Unit,
            onError: (PIncomingCallError) -> Unit,
        ) {
            val errorEnum =
                PhoneConnectionService.connectionManager
                    .checkAndReservePending(metadata.callId)

            if (errorEnum == null) {
                onSuccess()
            } else {
                onError(PIncomingCallError(errorEnum))
            }
        }
    }
}
