package com.webtrit.callkeep.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.managers.NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService

class IncomingCallNotificationBuilder : NotificationBuilder() {
    private var callMetaData: CallMetadata? = null

    fun setCallMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    private fun createCallActionIntent(action: String): PendingIntent {
        requireNotNull(callMetaData) { "Call metadata must be set before creating the intent." }

        val intent =
            Intent(context, IncomingCallService::class.java).apply {
                this.action = action
                putExtras(callMetaData!!.toBundle())
            }
        return PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun baseNotificationBuilder(
        title: String,
        text: String? = null,
    ): Notification.Builder =
        Notification.Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setContentTitle(title)
            text?.let { setContentText(it) }
            setAutoCancel(true)
            // Explicitly set PUBLIC visibility so the full notification content
            // is shown on the lock screen (channel-level VISIBILITY_PUBLIC is not
            // always inherited by individual notifications on MIUI/HyperOS).
            setVisibility(Notification.VISIBILITY_PUBLIC)
        }

    private fun createNotificationAction(
        iconRes: Int,
        textRes: Int,
        intent: PendingIntent,
    ): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(context, iconRes),
                context.getString(textRes),
                intent,
            ).build()

    override fun build(): Notification {
        val meta =
            requireNotNull(callMetaData) { "Call metadata must be set before building the notification." }

        val answerIntent = createCallActionIntent(NotificationAction.Answer.action)
        val declineIntent = createCallActionIntent(NotificationAction.Decline.action)

        val icDecline = R.drawable.ic_call_hungup
        val icAnswer = R.drawable.ic_call_answer

        val title = context.getString(R.string.incoming_call_title)
        val description = context.getString(R.string.incoming_call_description, meta.name)

        val answerButton = R.string.answer_call_button_text
        val declineButton = R.string.decline_button_text

        val builder =
            baseNotificationBuilder(title, description).apply {
                setOngoing(true)
                // Use full-screen intent only when both the app setting is enabled and the
                // system permission is granted.  On Android 14+ (API 34) the permission can
                // be revoked by the user; on MIUI/HyperOS it is denied by default for
                // third-party apps.  Passing a full-screen intent when the permission is
                // denied has no effect and produces a log warning, so we skip it and rely on
                // the WakeLock acquired in IncomingCallService as the fallback wake mechanism.
                val isFullScreenEnabled = StorageDelegate.IncomingCall.isFullScreen(context)
                val hasFullScreenPermission = PermissionsHelper(context).canUseFullScreenIntent()
                val canUseFullScreen = isFullScreenEnabled && hasFullScreenPermission
                Log.d(
                    TAG,
                    "fullScreenIntent: enabled=$isFullScreenEnabled permissionGranted=$hasFullScreenPermission → applied=$canUseFullScreen",
                )
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
            val style = Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent)
            builder
                .setStyle(style)
                .build()
                .apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        } else {
            builder.addAction(createNotificationAction(icDecline, declineButton, declineIntent))
            builder.addAction(createNotificationAction(icAnswer, answerButton, answerIntent))
            builder.build().apply { flags = flags or NotificationCompat.FLAG_INSISTENT }
        }
    }

    @SuppressLint("MissingPermission")
    fun buildSilent(): Notification {
        Log.d(TAG, "Updating incoming call notification to silent mode.")

        val meta =
            requireNotNull(callMetaData) { "Call metadata must be set before updating the notification." }

        val title = context.getString(R.string.incoming_call_title)
        val description = context.getString(R.string.incoming_call_description, meta.name)

        return NotificationCompat
            .Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // Intentionally no CATEGORY_CALL here: Samsung/MIUI force all CATEGORY_CALL
            // notifications to show as heads-up regardless of priority or silent flag.
            // This notification is a transitional FGS keepalive during teardown and must
            // not be visible to the user.
            .setContentTitle(title)
            .setContentText(description)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setFullScreenIntent(null, false)
            // Explicit PUBLIC visibility so the ongoing call notification remains
            // visible on the lock screen on MIUI/HyperOS after the ringing phase.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
            .apply {
                flags = flags and Notification.FLAG_INSISTENT.inv()
            }
    }

    fun buildReleaseNotification(): Notification {
        val builder =
            baseNotificationBuilder(
                title = context.getString(R.string.incoming_call_declined_title),
                text = context.getString(R.string.incoming_call_declined_text, callMetaData?.name),
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
        val meta = requireNotNull(callMetaData) { "Call metadata must be set before updating the notification." }
        NotificationManagerCompat.from(context).notify(notificationId(meta.callId), buildReleaseNotification())
    }

    companion object {
        const val TAG = "INCOMING_CALL_NOTIFICATION"

        /**
         * Returns a stable notification ID for the given call ID.
         *
         * Using a per-call ID ensures each incoming call is treated as a new
         * notification by the system, so the fullScreenIntent fires correctly
         * regardless of any previous call's notification.
         *
         * IDs are remapped into [MIN_CALL_NOTIFICATION_ID, Int.MAX_VALUE] to guarantee
         * they never collide with reserved IDs used elsewhere in the app
         * (FGS placeholder = 3, ActiveCallNotificationBuilder = 1, StandaloneCallService = 97).
         */
        fun notificationId(callId: String): Int = (callId.hashCode() and Int.MAX_VALUE).coerceAtLeast(MIN_CALL_NOTIFICATION_ID)

        // All per-call notification IDs are kept above this threshold so they never
        // collide with small reserved IDs used by other services in this package.
        private const val MIN_CALL_NOTIFICATION_ID = 1000
    }
}
