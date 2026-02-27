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
            ServiceAction.ForceUpdateAudioState -> metadata?.let { handleForceUpdateAudioState(it) }
        }
    }

    /**
     * Dispatches lifecycle events related to connection state changes.
     */
    fun dispatchLifecycle(action: ConnectionLifecycleAction, metadata: CallMetadata? = null) {
        logger.d("Dispatching LifecycleAction: $action")
        when (action) {
            ConnectionLifecycleAction.ConnectionCreated -> metadata?.let {
                handleConnectionCreated(it)
            }

            ConnectionLifecycleAction.ConnectionChanged -> handleConnectionChanged()
            ConnectionLifecycleAction.ServiceDestroyed -> handleServiceDestroyed()
        }
    }

    private fun handleAnswerCall(metadata: CallMetadata) {
        logger.d("Processing AnswerCall.")
        executeOnConnection(metadata, "AnswerCall", PhoneConnection::onAnswer)
        updateSensorsState()
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        executeOnConnection(metadata, "DeclineCall", PhoneConnection::declineCall)
        updateSensorsState()
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        executeOnConnection(metadata, "HungUpCall", PhoneConnection::hungUp)
        updateSensorsState()
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        logger.d("Processing EstablishCall. Updating sensors.")
        executeOnConnection(metadata, "EstablishCall", PhoneConnection::establish)
        updateSensorsState()
    }

    private fun handleMute(metadata: CallMetadata) {
        executeOnConnection(metadata, "SetMute(${metadata.hasMute})") {
            it.changeMuteState(metadata.hasMute ?: false)
        }
    }

    private fun handleHold(metadata: CallMetadata) {
        executeOnConnection(metadata, "SetHold(${metadata.hasHold})") {
            if (metadata.hasHold ?: false) it.onHold() else it.onUnhold()
        }
    }

    private fun handleUpdateCall(metadata: CallMetadata) {
        val connection = connectionManager.getConnection(metadata.callId)
        if (connection != null) {
            logger.v("Updating data for connection: ${metadata.callId}")
            connection.updateData(metadata)
        } else {
            logger.d("Connection not found for update, ignoring. CallId: ${metadata.callId}")
            return
        }

        updateSensorsState()
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
            it.toggleSpeaker(metadata.hasSpeaker ?: false)
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

    private fun handleForceUpdateAudioState(metadata: CallMetadata) {
        executeOnConnection(metadata, "ForceUpdateAudioState") { it.forceUpdateAudioState() }
    }

    private fun handleTearDown() {
        val connections = connectionManager.getConnections()
        logger.i("Tearing down all ${connections.size} active connections")

        connections.forEach { it.hungUp() }
        updateSensorsState()
    }

    private fun handleConnectionCreated(metadata: CallMetadata) {
        logger.d("Connection created for CallId: ${metadata.callId}. Syncing sensors state.")
        updateSensorsState()
    }

    private fun handleConnectionChanged() {
        logger.d("Connection list changed. Syncing sensors state.")
        updateSensorsState()
    }

    private fun handleServiceDestroyed() {
        logger.i("Service destroyed. Disposing resources.")

        runCatching {
            activityWakelockManager.releaseScreenWakeLock()
        }.onFailure { e ->
            logger.e("Failed to release screen wake lock", e)
        }

        runCatching {
            proximitySensorManager.setShouldListenProximity(false)
            proximitySensorManager.stopListening()
        }.onFailure { e ->
            logger.e("Failed to stop proximity sensor", e)
        }

        runCatching {
            activityWakelockManager.dispose()
        }.onFailure { e ->
            logger.e("Failed to dispose wakelock manager", e)
        }

        runCatching {
            val connections = connectionManager.getConnections()
            logger.i("Cleaning up ${connections.size} connections on service destroy")
            connections.forEach { connection ->
                runCatching {
                    connection.hungUp()
                }.onFailure { e ->
                    logger.e("Failed to hung up connection ${connection.callId}", e)
                }
            }
        }.onFailure { e ->
            logger.e("Failed to process connections cleanup", e)
        }
    }

    /**
     * Centralized logic to manage Proximity Sensor and Screen WakeLock based on
     * the global state of all connections.
     *
     * Rule:
     * 1. If ANY video connection exists -> Screen ON (WakeLock), Proximity OFF.
     * 2. If NO video but ANY audio connection -> Screen managed by Proximity (WakeLock OFF).
     * 3. If NO connections -> All sensors OFF.
     */
    private fun updateSensorsState() {
        // Get ONLY active (non-disconnected) connections directly
        val activeConnections = connectionManager.getConnections()
        val hasAnyConnection = activeConnections.isNotEmpty()

        // Check metadata specifically on the active connections list.
        // This avoids race conditions where the map might still contain a stale connection
        // or a connecting call hasn't updated its metadata yet.
        val hasVideo = activeConnections.any { it.hasVideo }
        // Proximity should only be enabled for audio-only calls that explicitly allow it.
        val shouldEnableProximity = activeConnections.any {
            !it.hasVideo && it.proximityEnabled
        }
        logger.v(
            "Updating sensors state. HasVideo: $hasVideo, HasAnyConnection: $hasAnyConnection, ShouldEnableProximity: $shouldEnableProximity"
        )
        if (hasVideo) {
            activityWakelockManager.acquireScreenWakeLock()
            proximitySensorManager.setShouldListenProximity(false)
            proximitySensorManager.stopListening()
        } else {
            activityWakelockManager.releaseScreenWakeLock()

            if (shouldEnableProximity) {
                proximitySensorManager.setShouldListenProximity(true)
                proximitySensorManager.startListening()
            } else {
                proximitySensorManager.setShouldListenProximity(false)
                proximitySensorManager.stopListening()
            }
        }
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
