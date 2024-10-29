package com.webtrit.callkeep.common.models

import kotlinx.serialization.Serializable

@Serializable
enum class BackgroundIncomingCallType {
    PUSH_NOTIFICATION,
    SOCKET
}
