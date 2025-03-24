package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.managers.NotificationChannelManager.NOTIFICATION_ACTIVE_CALL_CHANNEL_ID
import com.webtrit.callkeep.services.ActiveCallService

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

        val hungUpAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_call_hungup),
            context.getString(R.string.hang_up_button_text),
            getHungUpCallIntent(callsMetaData.first())
        ).build()

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
            addAction(hungUpAction)
        }

        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }

    private fun getHungUpCallIntent(callMetaData: CallMetadata): PendingIntent {
        val hangUpIntent = Intent(context, ActiveCallService::class.java).apply {
            action = NotificationAction.Hangup.action
            putExtras(callMetaData.toBundle())
        }
        return PendingIntent.getService(
            context, 0, hangUpIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val TAG = "ACTIVE_CALL_NOTIFICATION"
        const val NOTIFICATION_ID = 1
    }
}
