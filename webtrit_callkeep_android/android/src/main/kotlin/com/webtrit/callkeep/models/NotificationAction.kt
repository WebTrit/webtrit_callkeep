package com.webtrit.callkeep.models

import com.webtrit.callkeep.common.ContextHolder

enum class NotificationAction {
    Decline, Answer;

    val action: String
        get() = ContextHolder.appUniqueKey + name
}
