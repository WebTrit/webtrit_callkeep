package com.webtrit.callkeep.common

import java.util.concurrent.ConcurrentHashMap

/**
 * In-process queue for broadcasts that may fire before the target receiver is registered.
 *
 * Both sender and receiver must be in the same process. When a broadcast is sent to a
 * component that has not started yet, the sender posts an entry here. The receiver drains
 * the queue on startup and handles any pending entries as if the broadcast had just arrived.
 *
 * Keyed by an arbitrary string (e.g. "ACTION:callId"). Thread-safe. No TTL — entries persist
 * until consumed. Suitable for short-lived FGS-to-FGS communication within one app session.
 */
object PendingBroadcastQueue {
    private val queue = ConcurrentHashMap<String, Boolean>()

    fun post(key: String) {
        queue[key] = true
    }

    /** Returns true and removes the entry if [key] was present, false otherwise. */
    fun consume(key: String): Boolean = queue.remove(key) != null

    /** Key for a pending IC_RELEASE_WITH_DECLINE for [callId]. */
    fun incomingReleaseKey(callId: String) = "IC_RELEASE_WITH_DECLINE:$callId"
}
