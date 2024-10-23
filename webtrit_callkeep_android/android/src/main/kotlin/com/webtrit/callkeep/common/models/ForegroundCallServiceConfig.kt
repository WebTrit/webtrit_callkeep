package com.webtrit.callkeep.common.models

import kotlinx.serialization.Serializable

@Serializable
data class ForegroundCallServiceConfig(
    val callbackDispatcher: Long?,
    val onStartHandler: Long?,
    val onChangedLifecycleHandler: Long?,
    val androidNotificationName: String?,
    val androidNotificationDescription: String?,
    val autoRestartOnTerminate: Boolean,
    val autoStartOnBoot: Boolean,
)
