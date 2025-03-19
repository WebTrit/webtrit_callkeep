package com.webtrit.callkeep.models

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ForegroundCallServiceConfig(
    val androidNotificationName: String?,
    val androidNotificationDescription: String?
)