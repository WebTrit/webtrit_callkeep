package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context

class ForegroundCallNotificationBuilder() : NotificationBuilder() {
    private var title = ""
    private var content = ""

    fun setTitle(title: String) {
        this.title = title
    }

    fun setContent(content: String) {
        this.content = content
    }

    override fun registerNotificationChannel() {
        val notificationChannel = NotificationChannel(
            FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.push_notification_foreground_call_service_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.push_notification_foreground_call_service_description)
        }
        notificationManager.createNotificationChannel(notificationChannel)
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
        const val FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID = "FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID"
        const val FOREGROUND_CALL_NOTIFICATION_ID = 3 // R.integer.notification_incoming_call_id
    }
}
