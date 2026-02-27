package com.webtrit.callkeep

import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.toSignalingStatus
import com.webtrit.callkeep.services.broadcaster.SignalingStatusBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.foreground.ForegroundService

class ConnectionsApi() : PHostConnectionsApi {
    override fun getConnection(
        callId: String, callback: (Result<PCallkeepConnection?>) -> Unit
    ) {
        val metadata = ForegroundService.connectionTracker.get(callId)
        val pConn = metadata?.let {
            PCallkeepConnection(it.callId, PCallkeepConnectionState.STATE_ACTIVE,
                PCallkeepDisconnectCause(PCallkeepDisconnectCauseType.UNKNOWN, ""))
        }
        callback.invoke(Result.success(pConn))
    }

    override fun getConnections(callback: (Result<List<PCallkeepConnection>>) -> Unit) {
        val connections = ForegroundService.connectionTracker.getAll().map {
            PCallkeepConnection(it.callId, PCallkeepConnectionState.STATE_ACTIVE,
                PCallkeepDisconnectCause(PCallkeepDisconnectCauseType.UNKNOWN, ""))
        }
        callback.invoke(Result.success(connections))
    }

    override fun updateActivitySignalingStatus(
        status: PCallkeepSignalingStatus, callback: (Result<Unit>) -> Unit
    ) {
        SignalingStatusBroadcaster.setValue(ContextHolder.context, status.toSignalingStatus())
        callback(Result.success(Unit))
    }

    override fun cleanConnections(
        callback: (Result<Unit>) -> Unit
    ) {
        PhoneConnectionService.tearDown(ContextHolder.context)
        ForegroundService.connectionTracker.clear()
        callback(Result.success(Unit))
    }
}
