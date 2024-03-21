package com.webtrit.callkeep.webtrit_callkeep_android.api.background

import com.webtrit.callkeep.webtrit_callkeep_android.common.ApplicationData

enum class ReportAction {
    MissedCall, AcceptedCall;

    val action: String
        get() = ApplicationData.appUniqueKey + name
}
