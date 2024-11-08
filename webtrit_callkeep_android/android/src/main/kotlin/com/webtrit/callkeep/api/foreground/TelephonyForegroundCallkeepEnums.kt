package com.webtrit.callkeep.api.foreground

import com.webtrit.callkeep.common.ContextHolder


enum class ReportAction {
    AnswerCall, DeclineCall, OngoingCall, AudioMuting, ConnectionHolding, SentDTMF, DidPushIncomingCall, ConnectionHasSpeaker;

    val action: String
        get() = ContextHolder.appUniqueKey + name
}

enum class FailureAction {
    IncomingFailure, OutgoingFailure;

    val action: String
        get() = ContextHolder.appUniqueKey + name
}
