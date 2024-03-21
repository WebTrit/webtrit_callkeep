package com.webtrit.callkeep.webtrit_callkeep_android.api.foreground

import com.webtrit.callkeep.webtrit_callkeep_android.common.ApplicationData


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
