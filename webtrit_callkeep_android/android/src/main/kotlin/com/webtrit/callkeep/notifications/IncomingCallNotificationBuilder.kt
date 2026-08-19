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
import com.webtrit.callkeep.activities.AnswerCallTrampolineActivity
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

    /**
     * Decline goes straight to [IncomingCallService]: hanging up needs no user interface.
     */
    private fun createDeclineIntent(): PendingIntent {
        val metadata = requireNotNull(callMetaData) { "Call metadata must be set before creating the intent." }

        val intent =
            Intent(context, IncomingCallService::class.java).apply {
                this.action = NotificationAction.Decline.action
                putExtras(metadata.toBundle())
            }
        return PendingIntent.getService(
            context,
            requestCode(metadata, NotificationAction.Decline),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Answer goes through [AnswerCallTrampolineActivity] rather than straight to the service.
     *
     * Answering has to end with the call screen on top, and an app cannot start an activity
     * from a background service - the platform refuses it. An activity PendingIntent sent by
     * the system carries that permission with the user's tap, so the tap both answers the call
     * and opens the screen in one step. The trampoline forwards the answer to the same service
     * as before, so nothing downstream changes.
     */
    private fun createAnswerIntent(): PendingIntent {
        val metadata = requireNotNull(callMetaData) { "Call metadata must be set before creating the intent." }

        val intent =
            Intent(context, AnswerCallTrampolineActivity::class.java).apply {
                this.action = NotificationAction.Answer.action
                putExtras(metadata.toBundle())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        return PendingIntent.getActivity(
            context,
            requestCode(metadata, NotificationAction.Answer),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * A request code that is unique per call and per button.
     *
     * The system tells two pending intents apart by request code and by the intent's filter
     * fields - action, component and so on. It does not look at the extras, which is where the
     * call is named. With a fixed request code the buttons of a second call therefore reuse the
     * first call's pending intents, and [PendingIntent.FLAG_UPDATE_CURRENT] rewrites the first
     * notification's buttons to carry the second call. Pressing answer on the older
     * notification would then answer the newer call.
     */
    private fun requestCode(
        metadata: CallMetadata,
        action: NotificationAction,
    ): Int = notificationId(metadata.callId) + action.ordinal

    private fun baseNotificationBuilder(
        title: String,
        text: String? = null,
        smallIcon: Int = R.drawable.ic_notification,
    ): Notification.Builder =
        Notification.Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(smallIcon)
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

        val answerIntent = createAnswerIntent()
        val declineIntent = createDeclineIntent()

        val icDecline = R.drawable.ic_call_hungup
        val icAnswer = R.drawable.ic_call_answer

        val content = incomingCallContent(meta)

        val answerButton = R.string.answer_call_button_text
        val declineButton = R.string.decline_button_text

        val builder =
            baseNotificationBuilder(content.title, content.description, content.smallIcon).apply {
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
                    .setName(content.callerName)
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

        val content = incomingCallContent(meta)

        return NotificationCompat
            .Builder(context, INCOMING_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(content.smallIcon)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentTitle(content.title)
            .setContentText(content.description)
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
