package com.webtrit.callkeep

import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.toSignalingStatus
import com.webtrit.callkeep.services.broadcaster.SignalingStatusBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.foreground.MainProcessConnectionTracker

class ConnectionsApi() : PHostConnectionsApi {
    override fun getConnection(
        callId: String, callback: (Result<PCallkeepConnection?>) -> Unit
    ) {
        // Read from the main-process tracker instead of crossing to PhoneConnectionService.
        val connection = MainProcessConnectionTracker.instance.toPCallkeepConnection(callId)
        callback.invoke(Result.success(connection))
    }

    override fun getConnections(callback: (Result<List<PCallkeepConnection>>) -> Unit) {
        // Read from the main-process tracker instead of crossing to PhoneConnectionService.
        val tracker = MainProcessConnectionTracker.instance
        val connections = tracker.getAll()
            .mapNotNull { tracker.toPCallkeepConnection(it.callId) }
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
        // Clear both the tracker and the underlying ConnectionService state.
        MainProcessConnectionTracker.instance.clear()
        PhoneConnectionService.connectionManager.cleanConnections()
        callback(Result.success(Unit))
    }
}
