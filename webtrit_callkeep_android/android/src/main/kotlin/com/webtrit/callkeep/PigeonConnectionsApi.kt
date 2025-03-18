package com.webtrit.callkeep

import com.webtrit.callkeep.common.SignalingHolder
import com.webtrit.callkeep.models.toPConnection
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

class PigeonConnectionsApi() : PHostConnectionsApi {
    override fun getConnection(
        callId: String,
        callback: (Result<PCallkeepConnection?>) -> Unit
    ) {
        val connection = PhoneConnectionService.connectionManager.getConnection(callId)
        callback.invoke(Result.success(connection?.toPConnection()))
    }

    override fun updateActivitySignalingStatus(
        status: PCallkeepSignalingStatus,
        callback: (Result<Unit>) -> Unit
    ) {
        SignalingHolder.setStatus(status)
        callback(Result.success(Unit))
    }
}
