package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.Platform

abstract class NotificationBuilder {
    protected fun buildOpenAppIntent(
        context: Context,
        uri: Uri = Uri.EMPTY,
    ): PendingIntent {
        val hostAppActivity =
            Platform.getLaunchActivity(context)?.apply {
                data = uri
                // FLAG_TURN_SCREEN_ON and FLAG_SHOW_WHEN_LOCKED allow the Activity to
                // wake the screen and display over the keyguard when launched via a
                // full-screen intent. Without these flags the Activity may be started
                // silently behind the lock screen on Android 14+ and HyperOS devices.
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_TURN_SCREEN_ON or
                    Intent.FLAG_SHOW_WHEN_LOCKED
            }

        return PendingIntent.getActivity(
            context,
            R.integer.notification_incoming_call_id,
            hostAppActivity,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    abstract fun build(): Notification
}
