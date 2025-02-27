package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.models.CallMetadata

class ActiveCallNotificationBuilder(
    private val context: Context,
) : NotificationBuilder(context) {
    private var callsMetaData = ArrayList<CallMetadata>()

    fun setCallsMetaData(callsMetaData: List<CallMetadata>) {
        this.callsMetaData = callsMetaData as ArrayList<CallMetadata>
    }

    init {
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        val notificationChannel = NotificationChannel(
            NOTIFICATION_ACTIVE_CALL_CHANNEL_ID,
            context.getString(R.string.push_notification_active_call_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.push_notification_active_call_channel_description)
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }

    fun build(): Notification {
        val title = if (callsMetaData.size > 1) {
            context.getString(R.string.push_notification_active_calls_channel_title)
        } else {
            context.getString(R.string.push_notification_active_call_channel_title)
        }

        val text = callsMetaData.joinToString { it.name }

        val notificationBuilder = Notification.Builder(
            context, NOTIFICATION_ACTIVE_CALL_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentTitle(title)
            setContentText(text)
            setAutoCancel(false)
            setCategory(Notification.CATEGORY_SERVICE)
            setFullScreenIntent(buildOpenAppIntent(context), true)

        }
        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }

    override fun show() {}

    override fun hide() {}

    override fun cancel() {}


    companion object {
        const val TAG = "ACTIVE_CALL_NOTIFICATION"
        const val NOTIFICATION_ACTIVE_CALL_CHANNEL_ID = "NOTIFICATION_ACTIVE_CALL_CHANNEL_ID"
        const val ACTIVE_CALL_NOTIFICATION_ID = 1 //  R.integer.notification_active_call_id
    }
}
