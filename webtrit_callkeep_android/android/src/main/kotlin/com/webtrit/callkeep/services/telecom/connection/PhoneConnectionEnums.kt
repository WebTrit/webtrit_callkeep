package com.webtrit.callkeep.services.telecom.connection

import com.webtrit.callkeep.common.ContextHolder

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, Holding, UpdateCall, SendDTMF, IncomingCall, OutgoingCall, DetachActivity, TearDown;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_connection_service"
}
