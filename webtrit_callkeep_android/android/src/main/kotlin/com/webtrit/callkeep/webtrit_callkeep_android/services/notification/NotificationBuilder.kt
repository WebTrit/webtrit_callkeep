package com.webtrit.callkeep.webtrit_callkeep_android.services.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

import com.webtrit.callkeep.webtrit_callkeep_android.R
import com.webtrit.callkeep.webtrit_callkeep_android.common.helpers.Platform
import com.webtrit.callkeep.webtrit_callkeep_android.common.models.CallMetadata

abstract class NotificationBuilder {
    private var callMetaData: CallMetadata? = null
    private var notificationData: Map<String, Any> = mutableMapOf()

    abstract fun show()

    abstract fun hide()

    abstract fun cancel()

    fun getMetaData(): CallMetadata {
        return callMetaData!!
    }

    fun setMetaData(callMetaData: CallMetadata) {
        this.callMetaData = callMetaData
    }

    fun getNotificationData(): Map<String, Any> {
        return notificationData
    }

    fun setNotificationData(notificationData: Map<String, Any>) {
        this.notificationData = notificationData
    }

    fun getNotificationManager(
        context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun buildOpenAppIntent(context: Context, uri: Uri): PendingIntent {
        val hostAppActivity = Platform.getLaunchActivity(context)?.apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            R.integer.notification_incoming_call_id,
            hostAppActivity,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
