package com.webtrit.callkeep

import com.webtrit.callkeep.services.core.CallkeepCore

class ConnectionsApi : PHostConnectionsApi {
    private val core: CallkeepCore = CallkeepCore.instance

    override fun getConnection(
        callId: String,
        callback: (Result<PCallkeepConnection?>) -> Unit,
    ) {
        val connection = core.toPCallkeepConnection(callId)
        callback.invoke(Result.success(connection))
    }

    override fun getConnections(callback: (Result<List<PCallkeepConnection>>) -> Unit) {
        val connections =
            core
                .getAll()
                .mapNotNull { core.toPCallkeepConnection(it.callId) }
        callback.invoke(Result.success(connections))
    }

    override fun cleanConnections(callback: (Result<Unit>) -> Unit) {
        // Clear the shadow state and send CleanConnections command to :callkeep_core.
        // PhoneConnectionService handles it by calling connectionManager.cleanConnections()
        // on its own heap, which is safe cross-process after the split.
        core.clear()
        core.sendCleanConnections()
        callback(Result.success(Unit))
    }
}
