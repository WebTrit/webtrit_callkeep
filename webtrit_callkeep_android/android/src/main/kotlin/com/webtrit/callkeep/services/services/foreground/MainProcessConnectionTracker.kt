package com.webtrit.callkeep.services.services.foreground

import com.webtrit.callkeep.models.CallMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight tracker that mirrors connection state in the main process.
 * Updated via broadcast events (ConnectionAdded/ConnectionRemoved) from the core process.
 */
class MainProcessConnectionTracker {
    private val connections = ConcurrentHashMap<String, CallMetadata>()

    fun add(callId: String, metadata: CallMetadata) { connections[callId] = metadata }
    fun remove(callId: String) { connections.remove(callId) }
    fun exists(callId: String): Boolean = connections.containsKey(callId)
    fun getAll(): List<CallMetadata> = connections.values.toList()
    fun get(callId: String): CallMetadata? = connections[callId]
    fun clear() { connections.clear() }
    fun isEmpty(): Boolean = connections.isEmpty()
    override fun toString(): String = "Tracked connections: ${connections.keys}"
}
