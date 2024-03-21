package com.webtrit.callkeep.webtrit_callkeep_android.connection

import com.webtrit.callkeep.webtrit_callkeep_android.common.ApplicationData

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, Holding, UpdateCall, SendDtmf, IncomingCall, OutgoingCall, DetachActivity;

    val action: String
        get() = ApplicationData.appUniqueKey + name + "_connection_service"
}
