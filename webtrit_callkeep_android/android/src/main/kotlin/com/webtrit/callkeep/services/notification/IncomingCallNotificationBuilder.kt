package com.webtrit.callkeep.services.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.webtrit.callkeep.PigeonServiceApi
import com.webtrit.callkeep.R

class IncomingCallNotificationBuilder(
    private val context: Context
) : NotificationBuilder() {
    init {
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        val notificationChannel = NotificationChannel(
            INCOMING_CALL_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.push_notification_incoming_call_channel_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                context.getString(R.string.push_notification_incoming_call_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(null, null)
        }
        getNotificationManager(context).createNotificationChannel(notificationChannel)
    }

    private fun getAnsweredCallIntent(): PendingIntent {
        val answerIntent = Intent(PigeonServiceApi.ReportAction.Answer.action).apply {
            putExtras(getMetaData().toBundle())
        }

        return PendingIntent.getBroadcast(
            context,
            R.integer.notification_incoming_call_id,
            answerIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getHungUpCallIntent(): PendingIntent {
        val hangUpIntent = Intent(PigeonServiceApi.ReportAction.Hangup.action).apply {
            putExtras(getMetaData().toBundle())
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            hangUpIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun build(): Notification {
        val declineIntent = getHungUpCallIntent()
        val answerIntent = getAnsweredCallIntent()

        val answerAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_call_answer),
            context.getString(R.string.answer_call_button_text),
            answerIntent
        ).build()

        val declineAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_call_hungup),
            context.getString(R.string.hang_up_button_text),
            declineIntent
        ).build()

        val notificationBuilder = Notification.Builder(
            context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setContentTitle(context.getString(R.string.incoming_call_title))
            setContentText("You have an incoming call from ${getMetaData().name}")
            setAutoCancel(true)
            setFullScreenIntent(buildOpenAppIntent(context, getMetaData().getCallUri()), true)
        }

        val hasAnswerButton = hasAnswerButton()

        notificationBuilder.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasAnswerButton) {
                style = Notification.CallStyle.forIncomingCall(
                    Person.Builder().setName(getMetaData().name).setImportant(true).build(),
                    declineIntent,
                    answerIntent
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

    override fun cancel() {}

    override fun show() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(R.integer.notification_incoming_call_id, build())
    }

    override fun hide() {
        NotificationManagerCompat.from(context).cancel(R.integer.notification_incoming_call_id)
    }

    private fun hasAnswerButton(): Boolean = getNotificationData().getOrDefault(
        NOTIFICATION_DATA_HAS_ANSWER_BUTTON, true
    ) as Boolean

    companion object {
        const val INCOMING_CALL_NOTIFICATION_CHANNEL_ID = "INCOMING_CALL_NOTIFICATION_SILENT_CHANNEL_ID"
        const val NOTIFICATION_DATA_HAS_ANSWER_BUTTON = "NOTIFICATION_DATA_HAS_ANSWER_BUTTON"
    }
}
