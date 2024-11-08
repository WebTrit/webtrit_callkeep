package com.webtrit.callkeep.common.models

import com.webtrit.callkeep.common.ContextHolder

enum class NotificationAction {
    Hangup, Answer;

    val action: String
        get() = ContextHolder.appUniqueKey + name
}
