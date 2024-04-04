package com.webtrit.callkeep.connection

import com.webtrit.callkeep.common.ApplicationData

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, Holding, UpdateCall, SendDtmf, IncomingCall, OutgoingCall, DetachActivity;

    val action: String
        get() = ApplicationData.appUniqueKey + name + "_connection_service"
}
