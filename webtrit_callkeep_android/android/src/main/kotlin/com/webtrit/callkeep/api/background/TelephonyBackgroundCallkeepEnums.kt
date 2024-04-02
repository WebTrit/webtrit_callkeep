package com.webtrit.callkeep.api.background

import com.webtrit.callkeep.common.ApplicationData

enum class ReportAction {
    MissedCall, AcceptedCall;

    val action: String
        get() = ApplicationData.appUniqueKey + name
}
