package com.webtrit.callkeep.common.models

import kotlinx.serialization.Serializable

@Serializable
data class ForegroundCallServiceConfig(
    val type: BackgroundIncomingCallType? = null,
    val androidNotificationName: String?,
    val androidNotificationDescription: String?,
    val autoRestartOnTerminate: Boolean,
    val autoStartOnBoot: Boolean,
)