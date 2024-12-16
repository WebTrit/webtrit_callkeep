package com.webtrit.callkeep.models

import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepDisconnectCause
import com.webtrit.callkeep.PCallkeepDisconnectCauseType
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHandleTypeEnum
import com.webtrit.callkeep.services.telecom.connection.PhoneConnection

fun PHandle.toCallHandle(): CallHandle {
    return CallHandle(value)
}

fun CallHandle.toPHandle(): PHandle {
    return PHandle(value = number, type = PHandleTypeEnum.NUMBER)
}

fun PhoneConnection.toPConnection(): PCallkeepConnection? {
    val disconnectCause = disconnectCause ?: return null

    val callkeepStatus = PCallkeepConnectionState.ofRaw(state)
    val callkeepDisconnectCauseType = PCallkeepDisconnectCauseType.ofRaw(disconnectCause.code)

    if (callkeepStatus == null || callkeepDisconnectCauseType == null) {
        return null
    }

    val callkeepDisconnectCause =
        PCallkeepDisconnectCause(callkeepDisconnectCauseType, disconnectCause.reason ?: "Unknown reason")

    return PCallkeepConnection(metadata.callId, callkeepStatus, callkeepDisconnectCause)
}
