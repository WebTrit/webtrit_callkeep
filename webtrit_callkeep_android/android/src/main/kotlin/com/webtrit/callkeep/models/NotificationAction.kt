package com.webtrit.callkeep.models

enum class NotificationAction {
    Decline, Answer;

    val action: String
        get() = "callkeep_$name"
}
