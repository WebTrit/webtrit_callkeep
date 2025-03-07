package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.common.helpers.Platform

abstract class NotificationBuilder() {
    protected fun buildOpenAppIntent(context: Context, uri: Uri = Uri.EMPTY): PendingIntent {
        val hostAppActivity = Platform.getLaunchActivity(context)?.apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context, R.integer.notification_incoming_call_id, hostAppActivity, PendingIntent.FLAG_IMMUTABLE
        )
    }

    abstract fun build(): Notification;

}
