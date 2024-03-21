package com.webtrit.callkeep.webtrit_callkeep_android.api.foreground

import android.annotation.SuppressLint

import com.webtrit.callkeep.webtrit_callkeep_android.PCallRequestError
import com.webtrit.callkeep.webtrit_callkeep_android.PEndCallReason
import com.webtrit.callkeep.webtrit_callkeep_android.PIncomingCallError
import com.webtrit.callkeep.webtrit_callkeep_android.POptions
import com.webtrit.callkeep.webtrit_callkeep_android.common.models.CallMetadata

/**
 * Interface for managing foreground calls within the Webtrit CallKeep Android module.
 * Implementations of this interface handle incoming calls, call answers, hang-ups, and call termination.
 */

interface ForegroundCallkeepApi {
    /**
     * Set up the CallKeep with the given options.
     *
     * @param options The configuration options for CallKeep.
     * @param callback A callback function to handle the result of the setup operation.
     */
    fun setUp(options: POptions, callback: (Result<Unit>) -> Unit)

    /**
     * Start an outgoing call with the specified call metadata.
     *
     * @param metadata The metadata for the outgoing call.
     * @param callback A callback function to handle the result of the call request.
     */
    @SuppressLint("MissingPermission")
    fun startCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Report a new incoming call with the specified call metadata.
     *
     * @param metadata The metadata for the incoming call.
     * @param callback A callback function to handle the result of reporting the incoming call.
     */
    fun reportNewIncomingCall(metadata: CallMetadata, callback: (Result<PIncomingCallError?>) -> Unit)

    /**
     * Check if CallKeep has been set up.
     *
     * @return true if CallKeep is set up; otherwise, false.
     */
    fun isSetUp(): Boolean

    /**
     * Report that an outgoing call has been connected.
     *
     * @param metadata The metadata for the connected call.
     * @param callback A callback function to handle the result of reporting the connected call.
     */
    fun reportConnectedOutgoingCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit)

    /**
     * Report an update to the call metadata.
     *
     * @param metadata The updated metadata for the call.
     * @param callback A callback function to handle the result of updating the call.
     */
    fun reportUpdateCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit)

    /**
     * Report the end of a call with the specified call metadata and reason.
     *
     * @param metadata The metadata for the ended call.
     * @param reason The reason for ending the call.
     * @param callback A callback function to handle the result of ending the call.
     */
    fun reportEndCall(metadata: CallMetadata, reason: PEndCallReason, callback: (Result<Unit>) -> Unit)

    /**
     * Answer an incoming call with the specified call metadata.
     *
     * @param metadata The metadata for the incoming call to be answered.
     * @param callback A callback function to handle the result of answering the call.
     */
    fun answerCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * End an ongoing call with the specified call metadata.
     *
     * @param metadata The metadata for the ongoing call to be ended.
     * @param callback A callback function to handle the result of ending the call.
     */
    fun endCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Send DTMF (Dual-Tone Multi-Frequency) signals during a call with the specified call metadata.
     *
     * @param metadata The metadata for the ongoing call in which DTMF signals will be sent.
     * @param callback A callback function to handle the result of sending DTMF signals.
     */
    fun sendDTMF(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Set the call as muted or unmuted with the specified call metadata.
     *
     * @param metadata The metadata for the call to be muted/unmuted.
     * @param callback A callback function to handle the result of muting/unmuting the call.
     */
    fun setMuted(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Put the call on hold or unhold with the specified call metadata.
     *
     * @param metadata The metadata for the call to be held/unheld.
     * @param callback A callback function to handle the result of holding/unholding the call.
     */
    fun setHeld(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Enable or disable the speakerphone with the specified call metadata.
     *
     * @param metadata The metadata for the call to enable/disable the speakerphone.
     * @param callback A callback function to handle the result of enabling/disabling the speakerphone.
     */
    fun setSpeaker(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit)

    /**
     * Detach the activity from CallKeep.
     */
    fun detachActivity()
}
