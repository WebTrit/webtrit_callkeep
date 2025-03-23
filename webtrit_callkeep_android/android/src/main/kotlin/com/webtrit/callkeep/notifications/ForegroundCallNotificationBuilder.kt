package com.webtrit.callkeep.notifications

import android.app.Notification
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.managers.NotificationChannelManager.FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID

class ForegroundCallNotificationBuilder() : NotificationBuilder() {
    private var title = ""
    private var content = ""

    fun setTitle(title: String) {
        this.title = title
    }

    fun setContent(content: String) {
        this.content = content
    }

    override fun build(): Notification {
        if (title.isEmpty() || content.isEmpty()) throw IllegalStateException("Title and content must be set")

        val notificationBuilder = Notification.Builder(context, FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.call_foreground_icon)
            setContentTitle(title)
            setContentText(content)
            setAutoCancel(false)
            setOngoing(true)
            setCategory(Notification.CATEGORY_CALL)
            setVisibility(Notification.VISIBILITY_PUBLIC)

            setFullScreenIntent(
                buildOpenAppIntent(context), true
            )
            setContentIntent(buildOpenAppIntent(context))
        }
        val notification = notificationBuilder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        return notification
    }

    companion object {
        const val NOTIFICATION_ID = 3
    }
}
