package com.webtrit.callkeep.services.telecom.connection

import android.telecom.Connection
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

class ConnectionManager {
    private val connections: ConcurrentHashMap<String, PhoneConnection> = ConcurrentHashMap()
    private val connectionTimers: ConcurrentHashMap<String, TimerTask> = ConcurrentHashMap()
    private val terminatedConnections: MutableList<String> = mutableListOf()
    private val connectionResourceLock = Any()

    /**
     * Add a new connection with optional timeout handling.
     */
    // TODO(Serdun): The current modifier is incorrect; this method is public but should be restricted.
    // Consider limiting its accessibility to the connection service only.
    @Synchronized
    fun addConnection(
        callId: String,
        connection: PhoneConnection,
        timeout: Long? = null,
        validStates: List<Int> = listOf(Connection.STATE_RINGING, Connection.STATE_ACTIVE),
        onTimeout: (() -> Unit)? = null
    ) {
        synchronized(connectionResourceLock) {
            if (!connections.containsKey(callId)) {
                connections[callId] = connection

                if (timeout != null && onTimeout != null) {
                    startTimeout(callId, timeout, validStates, onTimeout)
                }
            }
        }
    }

    /**
     * Cancel an active timeout for a connection.
     */
    // TODO(Serdun): The current modifier is incorrect; this method is public but should be restricted.
    // Consider limiting its accessibility to the connection service only.
    fun cancelTimeout(callId: String) {
        synchronized(connectionResourceLock) {
            connectionTimers.remove(callId)?.cancel()
        }
    }

    /**
     * Start a timeout for a specific connection.
     */
    private fun startTimeout(
        callId: String, duration: Long, validStates: List<Int>, onTimeout: () -> Unit
    ) {
        val timerTask = object : TimerTask() {
            override fun run() {
                synchronized(connectionResourceLock) {
                    val connection = connections[callId]
                    if (connection != null && connection.state in validStates) {
                        onTimeout()
                        removeConnection(callId)
                    }
                }
            }
        }

        connectionTimers[callId] = timerTask
        Timer().schedule(timerTask, duration)
    }

    /**
     * Remove a connection by ID.
     */
    // TODO(Serdun): The current modifier is incorrect; this method is public but should be restricted.
    // Consider limiting its accessibility to the connection service only.
    @Synchronized
    fun removeConnection(callId: String) {
        synchronized(connectionResourceLock) {
            connections.remove(callId)
            cancelTimeout(callId)
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
    fun isConnectionTerminated(callId: String): Boolean {
        return terminatedConnections.contains(callId)
    }

    /**
     * Clear all terminated connections.
     */
    fun clearTerminatedConnections() {
        terminatedConnections.clear()
    }

    /**
     * Mark a connection as terminated.
     */
    fun addConnectionTerminated(id: String) {
        terminatedConnections.add(id)
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

            val timersInfo = connectionTimers.keys.joinToString(separator = ", ")
            val terminatedInfo = terminatedConnections.joinToString(separator = ", ")

            return """
            ConnectionManager {
                Active Connections:
                $connectionsInfo
                Timers:
                $timersInfo
                Terminated Connections:
                $terminatedInfo
            }
        """.trimIndent()
        }
    }
}
