package com.webtrit.callkeep.common.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R

class ForegroundCallNotificationBuilder(
    private val context: Context
) : NotificationBuilder() {
    init {
        registerNotificationChannel()
    }


    private fun registerNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel(
            FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.push_notification_missed_call_channel_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.push_notification_missed_call_channel_description)
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }

    fun build(title: String, content: String): Notification {
        val notificationBuilder = Notification.Builder(context, FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.primary_onboardin_logo)
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

    override fun cancel() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(FOREGROUND_CALL_NOTIFICATION_ID)

    }

    override fun show() {}

    override fun hide() {
        NotificationManagerCompat.from(context).deleteNotificationChannel(FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID)
    }

    companion object {
        const val FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID = "FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID"
        const val FOREGROUND_CALL_NOTIFICATION_ID = 3 // R.integer.notification_incoming_call_id
    }
}
