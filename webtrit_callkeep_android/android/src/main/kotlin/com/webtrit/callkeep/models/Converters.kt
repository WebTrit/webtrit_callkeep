package com.webtrit.callkeep.models

import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHandleTypeEnum

fun PHandle.toCallHandle(): CallHandle {
    return CallHandle(value);
}

fun CallHandle.toPHandle(): PHandle {
    return PHandle(value = number, type = PHandleTypeEnum.NUMBER)
}
