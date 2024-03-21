package com.webtrit.callkeep.webtrit_callkeep_android.common.models

import com.webtrit.callkeep.webtrit_callkeep_android.PHandle
import com.webtrit.callkeep.webtrit_callkeep_android.PHandleTypeEnum

fun PHandle.toCallHandle(): CallHandle {
    return CallHandle(value);
}

fun CallHandle.toPHandle(): PHandle {
    return PHandle(value = number, type = PHandleTypeEnum.NUMBER)
}
