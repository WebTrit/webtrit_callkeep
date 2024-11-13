package com.webtrit.callkeep.models

import kotlinx.serialization.Serializable

@Serializable
enum class BackgroundIncomingCallType {
    PUSH_NOTIFICATION,
    SOCKET
}
