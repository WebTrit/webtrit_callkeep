package com.webtrit.callkeep.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.NotificationChannelManager.NOTIFICATION_ACTIVE_CALL_CHANNEL_ID

class ActiveCallNotificationBuilder() : NotificationBuilder() {
    private var callsMetaData = ArrayList<CallMetadata>()

    fun setCallsMetaData(callsMetaData: List<CallMetadata>) {
        this.callsMetaData = callsMetaData as ArrayList<CallMetadata>
    }

    override fun build(): Notification {
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


    companion object {
        const val TAG = "ACTIVE_CALL_NOTIFICATION"
        const val ACTIVE_CALL_NOTIFICATION_ID = 1 //  R.integer.notification_active_call_id
    }
}
