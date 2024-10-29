package com.webtrit.callkeep.common.models

import com.webtrit.callkeep.PCallkeepIncomingType
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHandleTypeEnum

fun PHandle.toCallHandle(): CallHandle {
    return CallHandle(value);
}

fun CallHandle.toPHandle(): PHandle {
    return PHandle(value = number, type = PHandleTypeEnum.NUMBER)
}

fun PCallkeepIncomingType.toBackgroundIncomingCallType(): BackgroundIncomingCallType {
    return when (this) {
        PCallkeepIncomingType.PUSH_NOTIFICATION -> BackgroundIncomingCallType.PUSH_NOTIFICATION
        PCallkeepIncomingType.SOCKET -> BackgroundIncomingCallType.SOCKET
    }
}

fun BackgroundIncomingCallType.toPCallkeepIncomingType(): PCallkeepIncomingType {
    return when (this) {
        BackgroundIncomingCallType.PUSH_NOTIFICATION -> PCallkeepIncomingType.PUSH_NOTIFICATION
        BackgroundIncomingCallType.SOCKET -> PCallkeepIncomingType.SOCKET
    }
}
