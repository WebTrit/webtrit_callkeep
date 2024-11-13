package com.webtrit.callkeep.models

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ForegroundCallServiceHandles(
    val callbackDispatcher: Long,
    val onStartHandler: Long,
    val onChangedLifecycleHandler: Long,
)
