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
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.managers.NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.StandaloneCallService
import com.webtrit.callkeep.services.services.connection.StandaloneServiceAction

/**
 * Builds an incoming call notification for the standalone call path — used on devices that do
 * not expose the [android.software.telecom] system feature.
 *
 * Extends [NotificationBuilder] to reuse [buildOpenAppIntent] and keep behaviour consistent with
 * [IncomingCallNotificationBuilder]. Answer/Decline [PendingIntent]s are routed directly to
 * [StandaloneCallService] via [StandaloneServiceAction] so the user can interact with the
 * notification without the Telecom-backed
 * [com.webtrit.callkeep.services.services.incoming_call.IncomingCallService] being involved.
 */
internal class StandaloneIncomingCallNotificationBuilder : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    private fun createActionIntent(
        metadata: CallMetadata,
        action: StandaloneServiceAction,
    ): PendingIntent {
        val intent =
            Intent(context, StandaloneCallService::class.java).apply {
                this.action = action.action
                putExtras(metadata.toBundle())
            }
        return PendingIntent.getService(
            context,
            // Use ordinal as unique request code so Answer and Decline get distinct PendingIntents.
            action.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun baseBuilder(
        title: String,
        text: String,
    ): Notification.Builder =
        Notification.Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setContentTitle(title)
            setContentText(text)
            setOngoing(true)
            setAutoCancel(false)
            // Explicit PUBLIC visibility so the full notification content is shown on the lock
            // screen — channel-level VISIBILITY_PUBLIC is not always inherited on MIUI/HyperOS.
            setVisibility(Notification.VISIBILITY_PUBLIC)
        }

    override fun build(): Notification {
        val meta =
            requireNotNull(callMetaData) { "Call metadata must be set before building the notification." }

        val answerIntent = createActionIntent(meta, StandaloneServiceAction.AnswerCall)
        val declineIntent = createActionIntent(meta, StandaloneServiceAction.DeclineCall)

        val title = context.getString(R.string.incoming_call_title)
        val text = context.getString(R.string.incoming_call_description, meta.name)

        val builder =
            baseBuilder(title, text).apply {
                // Guard against a null launch intent before calling buildOpenAppIntent: if no
                // launchable activity exists, PendingIntent.getActivity() would receive a null
                // Intent and behave unpredictably on some API levels.
                val launchIntent = Platform.getLaunchActivity(context)
                val canUseFullScreen =
                    launchIntent != null &&
                        StorageDelegate.IncomingCall.isFullScreen(context) &&
                        PermissionsHelper(context).canUseFullScreenIntent()
                if (canUseFullScreen) {
                    setFullScreenIntent(buildOpenAppIntent(context), true)
                }
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person =
                Person
                    .Builder()
                    .setName(meta.name)
                    .setImportant(true)
                    .build()
            builder
                .setStyle(Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
                .build()
                .apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        } else {
            builder
                .addAction(
                    Notification.Action
                        .Builder(
                            Icon.createWithResource(context, R.drawable.ic_call_hungup),
                            context.getString(R.string.decline_button_text),
                            declineIntent,
                        ).build(),
                ).addAction(
                    Notification.Action
                        .Builder(
                            Icon.createWithResource(context, R.drawable.ic_call_answer),
                            context.getString(R.string.answer_call_button_text),
                            answerIntent,
                        ).build(),
                ).build()
                .apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        }
    }
}
