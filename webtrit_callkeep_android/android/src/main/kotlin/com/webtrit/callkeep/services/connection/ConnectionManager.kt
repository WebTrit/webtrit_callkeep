package com.webtrit.callkeep.services.connection

import android.telecom.Connection
import java.util.concurrent.ConcurrentHashMap
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.models.CallMetadata


class ConnectionManager {
    private val connections: ConcurrentHashMap<String, PhoneConnection> = ConcurrentHashMap()
    private val connectionResourceLock = Any()

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
            return connections.any { it.value.metadata.hasVideo }
        }
    }

    /**
     * Check if a connection is terminated.
     */
    fun isConnectionDisconnected(callId: String): Boolean {
        return connections[callId]?.state == Connection.STATE_DISCONNECTED
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
        }
    }

    /**
     * Checks whether the connection with the specified ID has been answered.
     *
     * @param id the identifier of the connection to check.
     * @return `true` if the connection has been answered, `false` otherwise.
     */
    fun isConnectionAnswered(id: String): Boolean {
        return connections[id]?.isAnswered() == true
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
            val manager = PhoneConnectionService.connectionManager

            when {
                manager.isConnectionDisconnected(metadata.callId) -> {
                    onError(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED))
                }

                manager.isConnectionAlreadyExists(metadata.callId) -> {
                    val errorEnum = if (manager.isConnectionAnswered(metadata.callId)) {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED
                    } else {
                        PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS
                    }
                    onError(PIncomingCallError(errorEnum))
                }

                else -> {
                    onSuccess()
                }
            }
        }
    }
}
