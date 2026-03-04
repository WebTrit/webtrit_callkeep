package com.webtrit.callkeep.services.services.foreground

import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.models.CallMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight tracker that mirrors connection state in the main process.
 * Updated via broadcast events (ConnectionAdded/ConnectionRemoved) from the core process.
 * Also tracks whether a connection has been answered (via AnswerCall broadcast) so that
 * when the main app reconnects to signaling after a background-isolate answer,
 * [ConnectionManager.validateConnectionAddition] can return CALL_ID_ALREADY_EXISTS_AND_ANSWERED.
 */
class MainProcessConnectionTracker {
    private val connections = ConcurrentHashMap<String, CallMetadata>()
    private val answeredCallIds = ConcurrentHashMap.newKeySet<String>()
    private val connectionStates = ConcurrentHashMap<String, PCallkeepConnectionState>()

    fun add(callId: String, metadata: CallMetadata) { connections[callId] = metadata }

    fun addWithState(callId: String, metadata: CallMetadata, state: PCallkeepConnectionState) {
        connections[callId] = metadata
        connectionStates[callId] = state
    }

    fun updateState(callId: String, state: PCallkeepConnectionState) {
        if (connections.containsKey(callId)) connectionStates[callId] = state
    }

    fun getState(callId: String): PCallkeepConnectionState =
        connectionStates[callId] ?: PCallkeepConnectionState.STATE_ACTIVE

    fun remove(callId: String) {
        connections.remove(callId)
        answeredCallIds.remove(callId)
        connectionStates.remove(callId)
    }
    fun exists(callId: String): Boolean = connections.containsKey(callId)
    fun getAll(): List<CallMetadata> = connections.values.toList()
    fun get(callId: String): CallMetadata? = connections[callId]
    fun clear() {
        connections.clear()
        answeredCallIds.clear()
        connectionStates.clear()
    }
    fun isEmpty(): Boolean = connections.isEmpty()

    /** Marks a connection as answered (called when AnswerCall broadcast is received). */
    fun markAnswered(callId: String) { answeredCallIds.add(callId) }

    /** Returns true if the connection has been answered via a native AnswerCall event. */
    fun isAnswered(callId: String): Boolean = answeredCallIds.contains(callId)

    override fun toString(): String =
        "Tracked connections: ${connections.keys}, answered: $answeredCallIds, states: $connectionStates"
}
