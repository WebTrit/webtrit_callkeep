package com.webtrit.callkeep.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.models.CallMetadata

abstract class NotificationBuilder(
    private val context: Context
) { 
    // TODO: move to specific child
    private var callMetaData: CallMetadata? = null
    // TODO: move to specific child
    private var notificationData: Map<String, Any> = mutableMapOf()

    protected val notificationManager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

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

    fun buildOpenAppIntent(context: Context, uri: Uri = Uri.EMPTY): PendingIntent {
        val hostAppActivity = Platform.getLaunchActivity(context)?.apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context, R.integer.notification_incoming_call_id, hostAppActivity, PendingIntent.FLAG_IMMUTABLE
        )
    }
}
