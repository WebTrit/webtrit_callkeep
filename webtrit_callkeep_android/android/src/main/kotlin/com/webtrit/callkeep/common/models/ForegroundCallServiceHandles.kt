package com.webtrit.callkeep.common.models

import kotlinx.serialization.Serializable

@Serializable
data class ForegroundCallServiceHandles(
    val callbackDispatcher: Long,
    val onStartHandler: Long,
    val onChangedLifecycleHandler: Long,
)
