package com.webtrit.callkeep.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.models.CallMetadata

abstract class NotificationBuilder {
    /** Resolved text and small icon for an incoming-call notification, branched on call type. */
    protected data class IncomingCallContent(
        val callerName: String,
        val title: String,
        val description: String,
        val smallIcon: Int,
    )

    /**
     * Builds the audio/video variant of the incoming-call notification content from [meta].
     *
     * Centralizes the [CallMetadata.hasVideo] branch so the ringing, silent and standalone
     * builders stay in sync. Note: on API 31+ the system [Notification.CallStyle] template
     * uses the caller's name as the title (so [title] is not shown) and renders its own
     * answer/decline button icons (so a video answer button is not possible there); the
     * [description] may still be shown as the body and the [smallIcon] is the reliable
     * video signal on every path. The full text variant applies on the API 26-30 and
     * silent (non-CallStyle) paths.
     */
    protected fun incomingCallContent(meta: CallMetadata): IncomingCallContent {
        val callerName = meta.name ?: context.getString(R.string.unknown_caller)
        val isVideo = meta.hasVideo == true
        return IncomingCallContent(
            callerName = callerName,
            title =
                context.getString(
                    if (isVideo) R.string.incoming_video_call_title else R.string.incoming_call_title,
                ),
            description =
                context.getString(
                    if (isVideo) R.string.incoming_video_call_description else R.string.incoming_call_description,
                    callerName,
                ),
            smallIcon = if (isVideo) R.drawable.ic_notification_video else R.drawable.ic_notification,
        )
    }

    protected fun buildOpenAppIntent(
        context: Context,
        uri: Uri = Uri.EMPTY,
    ): PendingIntent {
        val hostAppActivity =
            Platform.getLaunchActivity(context)?.apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
