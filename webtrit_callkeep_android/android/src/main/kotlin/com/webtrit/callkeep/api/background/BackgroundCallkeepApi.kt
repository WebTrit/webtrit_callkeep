package com.webtrit.callkeep.api.background

import com.webtrit.callkeep.common.models.CallMetadata

/**
 * Interface for managing background calls within the Webtrit CallKeep Android module.
 * Implementations of this interface handle incoming calls, call answers, hang-ups, and call termination.
 */
interface BackgroundCallkeepApi {
    /**
     * Registers a broadcast receiver to listen for events.
     */
    fun register()

    /**
     * Registers a broadcast receiver to listen for events.
     */
    fun unregister()

    /**
     * Create an incoming call.
     *
     * @param metadata The call metadata.
     * @param callback A callback to be invoked after processing the call.
     */
    fun incomingCall(
        metadata: CallMetadata, callback: (Result<Unit>) -> Unit
    )

    /**
     * Answer an incoming call.
     *
     * @param metadata The call metadata.
     */
    fun answer(
        metadata: CallMetadata
    )

    /**
     * Hang up an ongoing call.
     *
     * @param metadata The call metadata.
     * @param callback A callback to be invoked after hanging up the call.
     */
    fun hungUp(
        metadata: CallMetadata, callback: (Result<Unit>) -> Unit
    )

    /**
     * End an ongoing call.
     *
     * @param metadata The call metadata.
     */
    fun endCall(
        metadata: CallMetadata
    )

    /**
     * End all ongoing calls.
     *
     */
    fun endAllCalls()
}
