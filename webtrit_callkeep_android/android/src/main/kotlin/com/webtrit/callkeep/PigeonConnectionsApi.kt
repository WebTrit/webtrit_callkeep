package com.webtrit.callkeep

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
}
