package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleState

/**
 * Handles the lifecycle of an incoming call within a foreground Service:
 *  - shows the initial high-priority incoming-call notification
 *  - launches background handling (isolate) unless the main app is already active
 *  - transitions the notification to silent (ring muted) or releases it after answer
 *
 * This class is intentionally side-effectful and **does not** own Service lifecycle;
 * it only delegates to Service foreground APIs and the Notification builder.
 *
 * Thread-safety: methods are expected to be called on the main thread (Service thread).
 */
class IncomingCallHandler(
    private val service: Service,
    private val notificationBuilder: IncomingCallNotificationBuilder,
    private val isolateInitializer: IsolateInitializer,
) {
    private var lastMetadata: CallMetadata? = null
    private val notifier by lazy { NotificationManagerCompat.from(service) }

    /**
     * Entry point to process a fresh incoming call.
     * Shows the ringing notification and starts background handling unless the main app is active.
     */
    fun handle(metadata: CallMetadata) {
        Log.d(
            TAG,
            "Handling incoming call: id=${metadata.callId}, handle=${metadata.handle}, " + "name=${metadata.displayName}, video=${metadata.hasVideo}",
        )
        lastMetadata = metadata
        showNotification(metadata)
        maybeInitBackgroundHandling()
    }

    /**
     * Updates the notification based on call answer state:
     *  - answered=true  -> release incoming-call notification (builder-specific behavior)
     *  - answered=false -> mute: replace with a silent ongoing notification
     */
    @SuppressLint("MissingPermission")
    fun releaseIncomingCallNotification(answered: Boolean) {
        if (lastMetadata == null) {
            Log.w(TAG, "releaseIncomingCallNotification: no metadata (service not initialized), skipping")
            return
        }
        if (answered) {
            muteIncomingCallNotification()
        } else {
            notificationBuilder.updateToReleaseIncomingCallNotification()
        }
    }

    /**
     * Transitions the foreground Service to a silent call notification
     * (keeps the service in foreground, but cancels the loud ringing one).
     */
    @SuppressLint("MissingPermission")
    fun muteIncomingCallNotification() {
        stopForegroundDetach()
        notifier.cancel(IncomingCallNotificationBuilder.NOTIFICATION_ID)
        startForegroundCompat(notificationBuilder.buildSilent())
    }

    private fun showNotification(metadata: CallMetadata) {
        // Build a high-priority incoming call notification and elevate the Service to foreground.
        // foregroundServiceType must be passed explicitly: on API 34+ startForeground() without
        // a type throws InvalidForegroundServiceTypeException when the manifest declares one.
        val notification = notificationBuilder.apply { setCallMetaData(metadata) }.build()
        service.startForegroundServiceCompat(
            service,
            IncomingCallNotificationBuilder.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )
    }

    private fun maybeInitBackgroundHandling() {
        // Skip isolate launch when the main Flutter app is active (foreground or recently
        // backgrounded). In that state the main SignalingModule already has an open WebSocket
        // and handles the incoming call. Starting a second background isolate would open a
        // duplicate signaling connection, causing both sides to receive IncomingCallEvent and
        // fight over the same Telecom slot — resulting in callRejectedBySystem and a decline
        // loop. When the app is not active (killed / not yet started) the state is null or
        // ON_DESTROY, so the isolate launches normally.
        val state = ActivityLifecycleState.currentValue
        val isAppActive =
            state == Lifecycle.Event.ON_RESUME ||
                state == Lifecycle.Event.ON_PAUSE ||
                state == Lifecycle.Event.ON_STOP
        if (isAppActive) {
            Log.d(TAG, "maybeInitBackgroundHandling: app is active (state=$state), skipping isolate launch")
            return
        }
        Log.d(TAG, "Launching isolate for callId: ${lastMetadata?.callId}")
        isolateInitializer.start()
    }

    /**
     * Starts foreground respecting SDK level to avoid deprecated API warnings.
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(
                IncomingCallNotificationBuilder.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            service.startForeground(IncomingCallNotificationBuilder.NOTIFICATION_ID, notification)
        }
    }

    /**
     * Stops foreground without fully removing the Service (detach mode on Q+).
     * Keeps the Service running while detaching the old notification.
     */
    private fun stopForegroundDetach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.stopForeground(Service.STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            service.stopForeground(false)
        }
    }

    companion object {
        private const val TAG = "IncomingCallHandler"
    }
}
