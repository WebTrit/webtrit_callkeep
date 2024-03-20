package com.webtrit.callkeep.webtrit_callkeep_android.services.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.webtrit.callkeep.webtrit_callkeep_android.R

class MissedCallNotificationBuilder(
    private val context: Context
) : NotificationBuilder() {
    init {
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel(
            MISSED_CALL_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.push_notification_missed_call_channel_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                context.getString(R.string.push_notification_missed_call_channel_description)
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun build(): Notification {
        val notificationBuilder =
            Notification.Builder(context, MISSED_CALL_NOTIFICATION_CHANNEL_ID).apply {
                setSmallIcon(R.drawable.baseline_phone_missed_24)
                setContentTitle(context.getString(R.string.push_notification_missed_call_channel_title))
                setContentText("You have a missed call from ${getMetaData().name}")
                setAutoCancel(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setCategory(Notification.CATEGORY_MISSED_CALL)
                }
                setFullScreenIntent(
                    buildOpenAppIntent(context, getMetaData().getCallUri()), true
                )
            }
        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }

    override fun cancel() {
        val id = getMetaData().number.hashCode()
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(id)

    }

    override fun show() {
        val id = getMetaData().number.hashCode()
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(id, build())
    }

    override fun hide() {
        NotificationManagerCompat.from(context)
            .deleteNotificationChannel(MISSED_CALL_NOTIFICATION_CHANNEL_ID)
    }

    companion object {
        const val MISSED_CALL_NOTIFICATION_CHANNEL_ID = "MISSED_CALL_NOTIFICATION_CHANNEL_ID"
    }
}
