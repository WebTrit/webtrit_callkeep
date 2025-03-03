package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata

class MissedCallNotificationBuilder() : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    private fun getCallMetaData(): CallMetadata {
        return callMetaData ?: throw IllegalStateException("Call metadata is not set")
    }

    override fun registerNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel(
            MISSED_CALL_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.push_notification_missed_call_channel_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.push_notification_missed_call_channel_description)
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }

    override fun build(): Notification {
        val callMetaData = getCallMetaData()

        val notificationBuilder = Notification.Builder(context, MISSED_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.baseline_phone_missed_24)
            setContentTitle(context.getString(R.string.push_notification_missed_call_channel_title))
            setContentText("You have a missed call from ${callMetaData.name}")
            setAutoCancel(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setCategory(Notification.CATEGORY_MISSED_CALL)
            }
            setFullScreenIntent(
                buildOpenAppIntent(context, callMetaData.getCallUri()), true
            )
        }
        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }


    companion object {
        const val TAG = "MISSED_CALL_NOTIFICATION"
        const val MISSED_CALL_NOTIFICATION_CHANNEL_ID = "MISSED_CALL_NOTIFICATION_CHANNEL_ID"
    }
}
