package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.managers.NotificationChannelManager.ACTIVE_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.StandaloneCallService
import com.webtrit.callkeep.services.services.connection.StandaloneServiceAction

/**
 * Builds the ongoing (answered) call notification for the standalone call path - used on devices
 * that do not expose the [android.software.telecom] system feature.
 *
 * Counterpart of [StandaloneIncomingCallNotificationBuilder]: once a call is answered or
 * established, [StandaloneCallService] replaces its foreground incoming-call notification (which
 * still carries Answer/Decline actions) with this one, mirroring the Telecom path where the
 * incoming notification is cancelled on answer and an active-call notification is posted.
 * The Hang up action is routed directly to [StandaloneCallService] via
 * [StandaloneServiceAction.HungUpCall]; tapping the notification body opens the app.
 */
internal class StandaloneActiveCallNotificationBuilder : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    private fun createHangUpIntent(metadata: CallMetadata): PendingIntent {
        val intent =
            Intent(context, StandaloneCallService::class.java).apply {
                action = StandaloneServiceAction.HungUpCall.action
                putExtras(metadata.toBundle())
            }
        return PendingIntent.getService(
            context,
            // Same request-code scheme as StandaloneIncomingCallNotificationBuilder.createActionIntent:
            // the action ordinal keeps this PendingIntent distinct from the Answer/Decline ones.
            StandaloneServiceAction.HungUpCall.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun build(): Notification {
        val meta =
            requireNotNull(callMetaData) { "Call metadata must be set before building the notification." }

        val callerName = meta.name ?: context.getString(R.string.unknown_caller)
        val hangUpIntent = createHangUpIntent(meta)

        val builder =
            Notification.Builder(context, ACTIVE_CALL_SERVICE_NOTIFICATION_CHANNEL_ID).apply {
                setSmallIcon(if (meta.hasVideo == true) R.drawable.ic_notification_video else R.drawable.ic_notification)
                setCategory(NotificationCompat.CATEGORY_CALL)
                setContentTitle(context.getString(R.string.push_notification_active_call_channel_title))
                setContentText(callerName)
                setOngoing(true)
                setAutoCancel(false)
                setOnlyAlertOnce(true)
                setContentIntent(buildOpenAppIntent(context))
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person =
                Person
                    .Builder()
                    .setName(callerName)
                    .setImportant(true)
                    .build()
            builder
                .setStyle(Notification.CallStyle.forOngoingCall(person, hangUpIntent))
                .build()
        } else {
            builder
                .addAction(
                    Notification.Action
                        .Builder(
                            Icon.createWithResource(context, R.drawable.ic_call_hungup),
                            context.getString(R.string.hang_up_button_text),
                            hangUpIntent,
                        ).build(),
                ).build()
        }
    }
}
