package com.webtrit.callkeep.connection

import com.webtrit.callkeep.common.ContextHolder

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, Holding, UpdateCall, SendDtmf, IncomingCall, OutgoingCall, DetachActivity;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_connection_service"
}
