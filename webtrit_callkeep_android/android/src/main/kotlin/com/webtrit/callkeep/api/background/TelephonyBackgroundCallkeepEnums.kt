package com.webtrit.callkeep.api.background

import com.webtrit.callkeep.common.ContextHolder

enum class ReportAction {
    MissedCall, AcceptedCall, HungUp;

    val action: String
        get() = ContextHolder.appUniqueKey + name
}
