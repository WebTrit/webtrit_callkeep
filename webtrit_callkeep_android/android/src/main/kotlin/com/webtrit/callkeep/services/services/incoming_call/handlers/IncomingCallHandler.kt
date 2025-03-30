package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.annotation.SuppressLint
import android.app.Service
import android.util.Log
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.services.incoming_call.IsolateLaunchPolicy

class IncomingCallHandler(
    private val service: Service,
    private val notificationBuilder: IncomingCallNotificationBuilder,
    private val isolateLaunchPolicy: IsolateLaunchPolicy,
    private val isolateInitializer: IsolateInitializer
) {
    private var lastMetadata: CallMetadata? = null

    fun handle(metadata: CallMetadata) {
        lastMetadata = metadata
        showNotification(metadata)
        maybeInitBackgroundHandling()
    }

    private fun showNotification(metadata: CallMetadata) {
        service.startForegroundServiceCompat(
            service,
            IncomingCallNotificationBuilder.Companion.NOTIFICATION_ID,
            notificationBuilder.apply { setCallMetaData(metadata) }.build()
        )
    }

    @SuppressLint("MissingPermission")
    fun releaseIncomingCallNotification() {
        notificationBuilder.updateToReleaseIncomingCallNotification()
    }

    private fun maybeInitBackgroundHandling() {
        if (isolateLaunchPolicy.shouldLaunch()) {
            Log.d(TAG, "Launching isolate for callId: ${lastMetadata?.callId}")
            isolateInitializer.start()
        } else {
            Log.d(
                TAG,
                "Skipped launching isolate for callId: ${lastMetadata?.callId} isolateLaunchPolicy: ${isolateLaunchPolicy.shouldLaunch()} isolateInitializer: $isolateInitializer"
            )
        }
    }

    companion object {
        private const val TAG = "IncomingCallHandler"
    }
}