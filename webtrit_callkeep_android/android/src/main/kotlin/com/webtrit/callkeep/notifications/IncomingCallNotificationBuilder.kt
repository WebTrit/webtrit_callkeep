package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.managers.NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID
import com.webtrit.callkeep.services.IncomingCallService

class IncomingCallNotificationBuilder() : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null
    private var hasAnswerButton: Boolean = true

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    fun setHasAnswerButton(hasAnswerButton: Boolean) {
        this.hasAnswerButton = hasAnswerButton
    }

    private fun getCallMetaData(): CallMetadata {
        return callMetaData ?: throw IllegalStateException("Call metadata is not set")
    }

    private fun getAnsweredCallIntent(callMetaData: CallMetadata): PendingIntent {
        val answerIntent = Intent(context, IncomingCallService::class.java).apply {
            action = NotificationAction.Answer.action
            putExtras(callMetaData.toBundle())
        }
        return PendingIntent.getService(
            context, 0, answerIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getHungUpCallIntent(callMetaData: CallMetadata): PendingIntent {
        val hangUpIntent = Intent(context, IncomingCallService::class.java).apply {
            action = NotificationAction.Hangup.action
            putExtras(callMetaData.toBundle())
        }
        return PendingIntent.getService(
            context, 0, hangUpIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun build(): Notification {
        val callMetaData = getCallMetaData()

        val declineIntent = getHungUpCallIntent(callMetaData)
        val answerIntent = getAnsweredCallIntent(callMetaData)

        val answerAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_call_answer),
            context.getString(R.string.answer_call_button_text),
            answerIntent
        ).build()

        val declineAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_call_hungup),
            context.getString(R.string.decline_button_text),
            declineIntent
        ).build()

        val notificationBuilder = Notification.Builder(
            context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setContentTitle(context.getString(R.string.incoming_call_title))
            setContentText("You have an incoming call from ${callMetaData.name}")
            setAutoCancel(true)
            setFullScreenIntent(buildOpenAppIntent(context), true)
        }

        notificationBuilder.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasAnswerButton) {
                style = Notification.CallStyle.forIncomingCall(
                    Person.Builder().setName(callMetaData.name).setImportant(true).build(), declineIntent, answerIntent
                )
            } else {
                addAction(declineAction)
                if (hasAnswerButton) addAction(answerAction)
            }
        }

        val notification = notificationBuilder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        return notification
    }

    companion object {
        const val TAG = "INCOMING_CALL_NOTIFICATION"
        const val INCOMING_CALL_NOTIFICATION_ID = 1 // R.integer.notification_incoming_call_id
    }
}
