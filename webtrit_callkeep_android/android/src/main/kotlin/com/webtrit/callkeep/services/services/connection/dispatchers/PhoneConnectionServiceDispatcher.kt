package com.webtrit.callkeep.services.services.connection.dispatchers

import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.ConnectionManager
import com.webtrit.callkeep.services.services.connection.ProximitySensorManager
import com.webtrit.callkeep.services.services.connection.ServiceAction

class PhoneConnectionServiceDispatcher(
    private val connectionManager: ConnectionManager, private val proximitySensorManager: ProximitySensorManager,
) {
    fun dispatch(action: ServiceAction, metadata: CallMetadata?) {
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
        connectionManager.getConnection(metadata.callId)?.onAnswer()
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.declineCall()
        proximitySensorManager.stopListening()
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.hungUp()
        proximitySensorManager.stopListening()
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        proximitySensorManager.startListening()
        connectionManager.getConnection(metadata.callId)?.establish()
    }

    private fun handleMute(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.changeMuteState(metadata.hasMute)
    }

    private fun handleHold(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.apply {
            if (metadata.hasHold) onHold() else onUnhold()
        }
    }

    private fun handleUpdateCall(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.updateData(metadata)
    }

    private fun handleSendDTMF(metadata: CallMetadata) {
        metadata.dualToneMultiFrequency?.let {
            connectionManager.getConnection(metadata.callId)?.onPlayDtmfTone(it)
        }
    }

    private fun handleSpeaker(metadata: CallMetadata) {
        connectionManager.getConnection(metadata.callId)?.changeSpeakerState(metadata.hasSpeaker)
    }

    private fun handleTearDown() {
        connectionManager.getConnections().forEach {
            it.hungUp()
        }
    }
}