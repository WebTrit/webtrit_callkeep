package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context

/**
 * Singleton that manages the creation and registration of notification channels.
 */
object NotificationChannelManager {

    // Constants for notification channel IDs
    const val MISSED_CALL_NOTIFICATION_CHANNEL_ID = "MISSED_CALL_NOTIFICATION_CHANNEL_ID"
    const val INCOMING_CALL_NOTIFICATION_CHANNEL_ID = "INCOMING_CALL_NOTIFICATION_SILENT_CHANNEL_ID"
    const val FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID = "FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID"
    const val NOTIFICATION_ACTIVE_CALL_CHANNEL_ID = "NOTIFICATION_ACTIVE_CALL_CHANNEL_ID"

    /**
     * Registers all necessary notification channels.
     *
     * This method calls the individual methods to register channels for active calls,
     * incoming calls, missed calls, and foreground calls.
     *
     * @param context The context used to access system services and resources.
     */
    fun registerNotificationChannels(context: Context) {
        registerActiveCallChannel(context)
        registerIncomingCallChannel(context)
        registerMissedCallChannel(context)
        registerForegroundCallChannel(context)
    }

    /**
     * Registers the notification channel for active calls.
     *
     * This channel is used for notifications related to ongoing calls.
     *
     * @param context The context used to access system services and resources.
     */
    private fun registerActiveCallChannel(context: Context) {
        registerNotificationChannel(
            channelId = NOTIFICATION_ACTIVE_CALL_CHANNEL_ID,
            title = context.getString(R.string.push_notification_active_call_channel_title),
            description = context.getString(R.string.push_notification_active_call_channel_description),
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    /**
     * Registers the notification channel for incoming calls.
     *
     * This channel is used for notifications related to incoming calls with a high priority.
     *
     * @param context The context used to access system services and resources.
     */
    private fun registerIncomingCallChannel(context: Context) {
        registerNotificationChannel(
            channelId = INCOMING_CALL_NOTIFICATION_CHANNEL_ID,
            title = context.getString(R.string.push_notification_incoming_call_channel_title),
            description = context.getString(R.string.push_notification_incoming_call_channel_description),
            importance = NotificationManager.IMPORTANCE_HIGH,
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
            customSound = true,
            showBadge = true
        )
    }

    /**
     * Registers the notification channel for missed calls.
     *
     * This channel is used for notifications related to missed calls.
     *
     * @param context The context used to access system services and resources.
     */
    private fun registerMissedCallChannel(context: Context) {
        registerNotificationChannel(
            channelId = MISSED_CALL_NOTIFICATION_CHANNEL_ID,
            title = context.getString(R.string.push_notification_missed_call_channel_title),
            description = context.getString(R.string.push_notification_missed_call_channel_description),
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }

    /**
     * Registers the notification channel for foreground calls.
     *
     * This channel is used for notifications related to calls running in the foreground.
     *
     * @param context The context used to access system services and resources.
     */
    private fun registerForegroundCallChannel(context: Context) {
        registerNotificationChannel(
            channelId = FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID,
            title = context.getString(R.string.push_notification_foreground_call_service_title),
            description = context.getString(R.string.push_notification_foreground_call_service_description),
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }


    /**
     * Registers a notification channel with the provided parameters.
     *
     * @param channelId The ID of the notification channel.
     * @param title The title of the notification channel.
     * @param description A brief description of the notification channel's purpose.
     * @param importance The importance level of the notification channel.
     * @param showBadge Whether the channel should show a badge (default is true).
     * @param customSound Whether the channel should use a custom sound (default is false).
     * @param lockscreenVisibility The visibility of the notification on the lockscreen (default is public).
     */
    private fun registerNotificationChannel(
        channelId: String,
        title: String,
        description: String,
        importance: Int,
        showBadge: Boolean = true,
        customSound: Boolean = false,
        lockscreenVisibility: Int = Notification.VISIBILITY_PUBLIC
    ) {
        val notificationChannel = NotificationChannel(
            channelId, title, importance
        ).apply {
            this.description = description
            this.lockscreenVisibility = lockscreenVisibility
            setShowBadge(showBadge)
            if (customSound) setSound(null, null)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(notificationChannel)
    }
}