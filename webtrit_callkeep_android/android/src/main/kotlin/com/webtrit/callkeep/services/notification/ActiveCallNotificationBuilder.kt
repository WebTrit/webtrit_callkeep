package com.webtrit.callkeep.services.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

import androidx.core.app.NotificationManagerCompat

import com.webtrit.callkeep.R

class ActiveCallNotificationBuilder(
    private val context: Context,
) : NotificationBuilder() {
    init {
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        val notificationChannel = NotificationChannel(
            NOTIFICATION_ACTIVE_CALL_CHANNEL_ID,
            context.getString(R.string.push_notification_active_call_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description =
                context.getString(R.string.push_notification_active_call_channel_description)
        }
        getNotificationManager(context).createNotificationChannel(notificationChannel)
    }

    private fun build(): Notification {
        val notificationBuilder = Notification.Builder(
            context, NOTIFICATION_ACTIVE_CALL_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentTitle(context.getString(R.string.push_notification_active_call_channel_title))
            setContentText(getMetaData().name)
            setAutoCancel(false)
            setCategory(Notification.CATEGORY_SERVICE)
            setFullScreenIntent(buildOpenAppIntent(context, getMetaData().getCallUri()), true)

        }
        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }

    override fun cancel() {}

    override fun show() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(R.integer.notification_active_call_id, build())
    }

    override fun hide() {
        NotificationManagerCompat.from(context).cancel(R.integer.notification_active_call_id)
    }

    companion object {
        const val NOTIFICATION_ACTIVE_CALL_CHANNEL_ID = "NOTIFICATION_ACTIVE_CALL_CHANNEL_ID"
    }
}
