package com.webtrit.callkeep.services.telecom.connection

import android.telecom.Connection
import java.util.concurrent.ConcurrentHashMap

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

    fun getConnections(): List<PhoneConnection> {
        synchronized(connectionResourceLock) {
            return connections.values.toList()
        }
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
        return connections.get(callId)?.state == Connection.STATE_DISCONNECTED
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
     * Return active connection.
     */
    fun isExistsActiveConnection(): Boolean {
        synchronized(connectionResourceLock) {
            return getActiveConnection() != null
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

    /**
     * Returns the connection that is either active, new, or ringing.
     *
     * @return the matching [PhoneConnection] or `null` if none exist.
     */
    fun getActiveOrPendingConnection(): PhoneConnection? {
        return connections.values.find { it.state == Connection.STATE_NEW || it.state == Connection.STATE_RINGING || it.state == Connection.STATE_ACTIVE }
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
}
