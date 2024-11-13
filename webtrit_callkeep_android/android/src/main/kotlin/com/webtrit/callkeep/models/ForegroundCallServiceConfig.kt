package com.webtrit.callkeep.models

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ForegroundCallServiceConfig(
    val type: BackgroundIncomingCallType? = null,
    val androidNotificationName: String?,
    val androidNotificationDescription: String?,
    val autoRestartOnTerminate: Boolean,
    val autoStartOnBoot: Boolean,
)