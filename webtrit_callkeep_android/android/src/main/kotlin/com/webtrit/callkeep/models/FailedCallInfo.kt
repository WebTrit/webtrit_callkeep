package com.webtrit.callkeep.models

/**
 * Indicates where the outgoing call failure originated.
 */
enum class OutgoingFailureSource {
    /**
     * The call dispatch failed synchronously before reaching the ConnectionService —
     * e.g., an emergency number was dialled or startOutgoingCall() threw immediately.
     */
    DISPATCH_ERROR,

    /**
     * The app successfully dispatched the call, but Android Telecom or the ConnectionService
     * reported a failure asynchronously (e.g., connection failed, account issues).
     */
    CS_CALLBACK,

    /**
     * The call request timed out while waiting for a response from the ConnectionService.
     */
    TIMEOUT,
}

/**
 * Represents a snapshot of a failed outgoing call attempt.
 */
data class FailedCallInfo(
    val callId: String,
    val metadata: CallMetadata,
    val source: OutgoingFailureSource,
    val reason: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
