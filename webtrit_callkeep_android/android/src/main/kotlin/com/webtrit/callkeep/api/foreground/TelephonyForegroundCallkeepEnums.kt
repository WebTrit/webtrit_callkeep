package com.webtrit.callkeep.api.foreground

import com.webtrit.callkeep.common.ApplicationData


enum class ReportAction {
    AnswerCall, DeclineCall, OngoingCall, AudioMuting, ConnectionHolding, SentDTMF, DidPushIncomingCall, ConnectionHasSpeaker;

    val action: String
        get() = ApplicationData.appUniqueKey + name
}

enum class FailureAction {
    IncomingFailure, OutgoingFailure;

    val action: String
        get() = ApplicationData.appUniqueKey + name
}
