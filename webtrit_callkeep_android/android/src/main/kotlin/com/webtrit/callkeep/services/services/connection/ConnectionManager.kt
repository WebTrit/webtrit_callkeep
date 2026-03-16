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
            return when {
                pendingCallIds.contains(callId) ->
                    PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS

                connections[callId]?.state == Connection.STATE_DISCONNECTED ->
                    PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED

                connections.containsKey(callId) ->
                    if (connections[callId]?.hasAnswered == true) {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED
                    } else {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS
                    }

                else -> {
                    pendingCallIds.add(callId)
                    null
                }
            }
        }
    }

    fun removePending(callId: String) {
        pendingCallIds.remove(callId)
    }

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

    // TODO(Serdun): The current modifier is incorrect; this method is public but should be restricted.
    // Consider limiting its accessibility to the connection service only.
    @Synchronized
    fun addConnection(
        callId: String,
        connection: PhoneConnection,
    ) {
        synchronized(connectionResourceLock) {
            if (!connections.containsKey(callId)) {
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
    fun getConnections(): List<PhoneConnection> = synchronized(connectionResourceLock) {
        connections.values.filter { it.state != Connection.STATE_DISCONNECTED }
    }

    /**
     * Check if a connection already exists.
     */
    fun isConnectionAlreadyExists(callId: String): Boolean {
        synchronized(connectionResourceLock) {
            return connections.containsKey(callId)
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
     */
    fun reserveAnswer(callId: String) {
        pendingAnswers.add(callId)
    }

    /**
     * Consumes and returns whether a deferred answer was reserved for [callId].
     * Returns true and removes the reservation if one exists; returns false otherwise.
     */
    fun consumeAnswer(callId: String): Boolean {
        return pendingAnswers.remove(callId)
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
    fun isConnectionAnswered(id: String): Boolean {
        return connections[id]?.hasAnswered == true
    }

    override fun toString(): String {
        synchronized(connectionResourceLock) {
            val connectionsInfo = connections.map { (callId, connection) ->
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
            metadata: CallMetadata, onSuccess: () -> Unit, onError: (PIncomingCallError) -> Unit
        ) {
            val errorEnum = PhoneConnectionService.connectionManager
                .checkAndReservePending(metadata.callId)

            if (errorEnum == null) onSuccess()
            else onError(PIncomingCallError(errorEnum))
        }
    }
}
