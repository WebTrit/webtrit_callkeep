package com.webtrit.callkeep.services.services.connection.dispatchers

import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.services.connection.ConnectionManager
import com.webtrit.callkeep.services.services.connection.ProximitySensorManager
import com.webtrit.callkeep.services.services.connection.ServiceAction
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle

/**
 * Dispatcher for handling call-related service actions triggered from various sources,
 * such as activities, background signaling, or incoming services.
 *
 * This class is responsible for forwarding service actions to the appropriate connection instance
 * via the [ConnectionManager], and for managing the [ProximitySensorManager].
 * If a corresponding connection does not exist, it uses the [PerformDispatchHandle] proxy
 * to forward the action back (e.g., to Flutter) to avoid freezing async/await logic.
 *
 * @property connectionManager Manages active phone connections.
 * @property proximitySensorManager Controls the proximity sensor behavior.
 * @property dispatcher Proxy used to report events if no connection is found.
 */
class PhoneConnectionServiceDispatcher(
    private val connectionManager: ConnectionManager,
    private val proximitySensorManager: ProximitySensorManager,
    private val dispatcher: PerformDispatchHandle
) {

    /**
     * Dispatches a given [ServiceAction] with optional [CallMetadata] to the appropriate
     * connection or fallback dispatcher.
     *
     * The method routes the action to its corresponding handler based on the action type.
     * If the associated connection exists, the action is executed directly on it.
     * Otherwise, the fallback [dispatcher] is used (e.g., to notify Flutter or other layers).
     *
     * @param action The service action to be performed.
     * @param metadata Metadata associated with the call, if applicable.
     */
    fun dispatch(action: ServiceAction, metadata: CallMetadata?) {
        Log.d(TAG, "Dispatching action: $action with metadata: $metadata")
        when (action) {
            ServiceAction.AnswerCall -> metadata?.let { handleAnswerCall(it) }
            ServiceAction.DeclineCall -> metadata?.let { handleDeclineCall(it) }
            ServiceAction.HungUpCall -> metadata?.let { handleHungUpCall(it) }
            ServiceAction.EstablishCall -> metadata?.let { handleEstablishCall(it) }
            ServiceAction.Muting -> metadata?.let { handleMute(it) }
            ServiceAction.Holding -> metadata?.let { handleHold(it) }
            ServiceAction.UpdateCall -> metadata?.let { handleUpdateCall(it) }
            ServiceAction.SendDTMF -> metadata?.let { handleSendDTMF(it) }
            ServiceAction.Speaker -> metadata?.let { handleSpeaker(it) }
            ServiceAction.TearDown -> handleTearDown()
        }
    }

    private fun handleAnswerCall(metadata: CallMetadata) {
        proximitySensorManager.startListening()
        connectionManager.getConnection(metadata.callId)?.onAnswer() ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.declineCall() ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
        proximitySensorManager.stopListening()
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.hungUp() ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
        proximitySensorManager.stopListening()
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        proximitySensorManager.startListening()
        connectionManager.getConnection(metadata.callId)?.establish() ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    private fun handleMute(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.changeMuteState(metadata.hasMute) ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    private fun handleHold(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.apply {
            if (metadata.hasHold) onHold() else onUnhold()
        } ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    // This method updates the call metadata. It can be invoked during the connection creation process,
    // where the connection might not yet be registered in the ConnectionManager. If no connection is found,
    // the update is ignored to prevent errors.
    private fun handleUpdateCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.updateData(metadata) ?: Log.d(
            TAG, "Connection not found for callId: ${metadata.callId}, ignoring update"
        )
    }

    private fun handleSendDTMF(metadata: CallMetadata) {
        metadata.dualToneMultiFrequency?.let {
            connectionManager.getConnection(metadata.callId)?.onPlayDtmfTone(it)
        } ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    private fun handleSpeaker(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.changeSpeakerState(metadata.hasSpeaker) ?: dispatcher(
            ConnectionPerform.ConnectionNotFound, metadata
        )
    }

    private fun handleTearDown() {
        connectionManager.getConnections().forEach {
            it.hungUp()
        }
    }

    companion object {
        private const val TAG = "PhoneConnectionServiceDispatcher"
    }
}