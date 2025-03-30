package com.webtrit.callkeep.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.managers.NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService

class IncomingCallNotificationBuilder() : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    private fun createCallActionIntent(action: String): PendingIntent {
        requireNotNull(callMetaData) { "Call metadata must be set before creating the intent." }

        val intent = Intent(context, IncomingCallService::class.java).apply {
            this.action = action
            putExtras(callMetaData!!.toBundle())
        }
        return PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun baseNotificationBuilder(title: String, text: String? = null): Notification.Builder {
        return Notification.Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setContentTitle(title)
            text?.let { setContentText(it) }
            setAutoCancel(true)
        }
    }

    private fun createNotificationAction(iconRes: Int, textRes: Int, intent: PendingIntent): Notification.Action {
        return Notification.Action.Builder(
            Icon.createWithResource(context, iconRes), context.getString(textRes), intent
        ).build()
    }

    override fun build(): Notification {
        val meta = requireNotNull(callMetaData) { "Call metadata must be set before building the notification." }

        val answerIntent = createCallActionIntent(NotificationAction.Answer.action)
        val declineIntent = createCallActionIntent(NotificationAction.Decline.action)

        val icDecline = R.drawable.ic_call_hungup
        val icAnswer = R.drawable.ic_call_answer

        val title = context.getString(R.string.incoming_call_title)
        val description = context.getString(R.string.incoming_call_declined_text, meta.name)

        val answerButton = R.string.answer_call_button_text
        val declineButton = R.string.decline_button_text

        val builder = baseNotificationBuilder(title, description).apply {
            setOngoing(true)
            setFullScreenIntent(buildOpenAppIntent(context), true)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(meta.name).setImportant(true).build()
            val style = Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent)
            builder.setStyle(style).build().apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        } else {
            builder.addAction(createNotificationAction(icDecline, declineButton, declineIntent))
            builder.addAction(createNotificationAction(icAnswer, answerButton, answerIntent))
            builder.build().apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        }
    }

    fun buildReleaseNotification(): Notification {
        val builder = baseNotificationBuilder(
            title = context.getString(R.string.incoming_call_declined_title),
            text = context.getString(R.string.incoming_call_declined_text, callMetaData?.name)
        ).apply {
            setFullScreenIntent(null, false)
            setTimeoutAfter(5_000)

        }
        return builder.build().apply {
            flags = flags or NotificationCompat.FLAG_INSISTENT
        }
    }

    @SuppressLint("MissingPermission")
    fun updateToReleaseIncomingCallNotification() {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildReleaseNotification())
    }

    companion object {
        const val TAG = "INCOMING_CALL_NOTIFICATION"
        const val NOTIFICATION_ID = 2
    }
}
