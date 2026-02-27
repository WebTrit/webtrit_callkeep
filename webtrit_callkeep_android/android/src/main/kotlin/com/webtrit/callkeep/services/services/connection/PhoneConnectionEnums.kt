package com.webtrit.callkeep.services.services.connection

import com.webtrit.callkeep.common.ContextHolder

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, AudioDeviceSet, Holding, UpdateCall, SendDTMF, TearDown, ForceUpdateAudioState;

    companion object {
        fun from(action: String?): ServiceAction {
            return ServiceAction.entries.find { it.action == action }
                ?: throw IllegalArgumentException("Unknown action: $action")
        }
    }

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_connection_service"
}
