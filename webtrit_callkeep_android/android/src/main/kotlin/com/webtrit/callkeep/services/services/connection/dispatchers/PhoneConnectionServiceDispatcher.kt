package com.webtrit.callkeep.services.services.connection.dispatchers

import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.services.connection.ActivityWakelockManager
import com.webtrit.callkeep.services.services.connection.ConnectionManager
import com.webtrit.callkeep.services.services.connection.PhoneConnection
import com.webtrit.callkeep.services.services.connection.ProximitySensorManager
import com.webtrit.callkeep.services.services.connection.ServiceAction
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle

enum class ConnectionLifecycleAction {
    ConnectionCreated, ConnectionChanged, ServiceDestroyed
}

/**
 * Dispatcher responsible for routing ServiceAction requests to PhoneConnection instances
 * managed by the [ConnectionManager], and for coordinating ancillary managers such as
 * [ProximitySensorManager] and [ActivityWakelockManager].
 *
 * If the targeted connection cannot be found, the provided [PerformDispatchHandle]
 * (`dispatcher`) is invoked to propagate the event to the higher layer (for example, Flutter)
 * rather than blocking async logic.
 *
 * @property connectionManager Manages active phone connections.
 * @property dispatcher Callback invoked when a connection is not found.
 * @property activityWakelockManager Controls screen wake locks (used for video calls).
 * @property proximitySensorManager Controls proximity sensor behavior.
 */
class PhoneConnectionServiceDispatcher(
    private val connectionManager: ConnectionManager,
    private val dispatcher: PerformDispatchHandle,
    private val activityWakelockManager: ActivityWakelockManager,
    private val proximitySensorManager: ProximitySensorManager,
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
        logger.d("Dispatching ServiceAction: $action | CallId: ${metadata?.callId ?: "N/A"}")

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
            ServiceAction.AudioDeviceSet -> metadata?.let { handleAudioDeviceSet(it) }
            ServiceAction.TearDown -> handleTearDown()
        }
    }

    /**
     * Dispatches lifecycle events related to connection state changes.
     */
    fun dispatchLifecycle(action: ConnectionLifecycleAction, metadata: CallMetadata? = null) {
        logger.d("Dispatching LifecycleAction: $action")
        when (action) {
            ConnectionLifecycleAction.ConnectionCreated -> metadata?.let {
                handleConnectionCreated(
                    it
                )
            }

            ConnectionLifecycleAction.ConnectionChanged -> handleConnectionChanged()
            ConnectionLifecycleAction.ServiceDestroyed -> handleServiceDestroyed()
        }
    }

    private fun handleAnswerCall(metadata: CallMetadata) {
        logger.d("Starting proximity sensor for AnswerCall")
        proximitySensorManager.startListening()

        executeOnConnection(metadata, "AnswerCall") {
            it.onAnswer()
        }
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        executeOnConnection(metadata, "DeclineCall") {
            it.declineCall()
        }

        logger.d("Stopping proximity sensor (DeclineCall)")
        proximitySensorManager.stopListening()
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        executeOnConnection(metadata, "HungUpCall") {
            it.hungUp()
        }

        logger.d("Stopping proximity sensor (HungUpCall)")
        proximitySensorManager.stopListening()
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        logger.d("Starting proximity sensor for EstablishCall")
        proximitySensorManager.startListening()

        executeOnConnection(metadata, "EstablishCall") {
            it.establish()
        }
    }

    private fun handleMute(metadata: CallMetadata) {
        executeOnConnection(metadata, "SetMute(${metadata.hasMute})") {
            it.changeMuteState(metadata.hasMute)
        }
    }

    private fun handleHold(metadata: CallMetadata) {
        executeOnConnection(metadata, "SetHold(${metadata.hasHold})") {
            if (metadata.hasHold) it.onHold() else it.onUnhold()
        }
    }

    private fun handleUpdateCall(metadata: CallMetadata) {
        if (metadata.hasVideo) {
            activityWakelockManager.acquireScreenWakeLock()
        } else {
            activityWakelockManager.releaseScreenWakeLock()
        }

        val connection = connectionManager.getConnection(metadata.callId)
        if (connection != null) {
            logger.v("Updating data for connection: ${metadata.callId}")
            connection.updateData(metadata)
        } else {
            logger.d("Connection not found for update, ignoring. CallId: ${metadata.callId}")
        }
    }

    private fun handleSendDTMF(metadata: CallMetadata) {
        val dtmf = metadata.dualToneMultiFrequency
        if (dtmf == null) {
            logger.w("DTMF action requested but no tone provided. CallId: ${metadata.callId}")
            return
        }

        executeOnConnection(metadata, "PlayDTMF($dtmf)") {
            it.onPlayDtmfTone(dtmf)
        }
    }

    private fun handleSpeaker(metadata: CallMetadata) {
        executeOnConnection(metadata, "SetSpeaker(${metadata.hasSpeaker})") {
            it.changeSpeakerState(metadata.hasSpeaker)
        }
    }

    private fun handleAudioDeviceSet(metadata: CallMetadata) {
        val device = metadata.audioDevice
        if (device == null) {
            logger.e("AudioDeviceSet requested but device is null. CallId: ${metadata.callId}")
            return
        }

        executeOnConnection(metadata, "SetAudioDevice($device)") {
            it.setAudioDevice(device)
        }
    }

    private fun handleTearDown() {
        val connections = connectionManager.getConnections()
        logger.i("Tearing down all ${connections.size} active connections")

        connections.forEach { it.hungUp() }
    }


    private fun handleConnectionCreated(metadata: CallMetadata) {
        if (metadata.hasVideo) {
            logger.d("Video connection created. Requesting Screen WakeLock. CallId: ${metadata.callId}")
            activityWakelockManager.acquireScreenWakeLock()
        }

        // Handle Proximity Sensor Logic
        // If it is a video call, the proximity sensor should be disabled.
        // If it is an audio call, we respect the 'proximityEnabled' flag (defaulting to true for audio).
        val shouldListenProximity = if (metadata.hasVideo) false else metadata.proximityEnabled
        logger.d("Setting proximity listen state to: $shouldListenProximity for callId: ${metadata.callId}")
        proximitySensorManager.setShouldListenProximity(shouldListenProximity)
    }

    private fun handleConnectionChanged() {
        if (!connectionManager.hasVideoConnections()) {
            logger.d("No active video connections remaining. Releasing Screen WakeLock.")
            activityWakelockManager.releaseScreenWakeLock()
        }
        proximitySensorManager.stopListening()
    }

    private fun handleServiceDestroyed() {
        logger.i("Service destroyed. Disposing resources.")
        activityWakelockManager.dispose()
    }

    /**
     * Helper to safely execute an action on a connection.
     * If the connection is missing, it logs a warning and dispatches [ConnectionPerform.ConnectionNotFound].
     */
    private inline fun executeOnConnection(
        metadata: CallMetadata, actionName: String, block: (PhoneConnection) -> Unit
    ) {
        val connection = connectionManager.getConnection(metadata.callId)
        if (connection != null) {
            block(connection)
        } else {
            logger.w("Unable to perform $actionName: Connection not found for callId: ${metadata.callId}")
            dispatcher(ConnectionPerform.ConnectionNotFound, metadata)
        }
    }

    companion object {
        private const val TAG = "PhoneConnectionServiceDispatcher"
        private val logger = Log(TAG)
    }
}
