package com.webtrit.callkeep.services.services.active_call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.parcelableArrayList
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.ActiveCallNotificationBuilder
import com.webtrit.callkeep.services.core.CallkeepCore

class ActiveCallService : Service() {
    private val activeCallNotificationBuilder = ActiveCallNotificationBuilder()
    private var callsMetadata = mutableListOf<CallMetadata>()

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        AssetCacheManager.init(applicationContext)
        Log.initFromContext(applicationContext)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Handle the hangup action from the notification
        if (NotificationAction.Decline.action == intent?.action) {
            hungUpCall()

            return START_NOT_STICKY
        }

        callsMetadata =
            intent
                ?.parcelableArrayList<Bundle>("metadata")
                ?.map { CallMetadata.fromBundle(it) }
                ?.toMutableList() ?: mutableListOf()

        activeCallNotificationBuilder.setCallsMetaData(callsMetadata)
        val notification = activeCallNotificationBuilder.build()

        // startForeground must be called even when there are no calls to show: the service may
        // have been (re)started as foreground and skipping the promotion would kill the process
        // with ForegroundServiceDidNotStartInTimeException.
        //
        // On Android 14+ the MICROPHONE type is rejected with SecurityException when the
        // promotion happens from the background - which is exactly the START_STICKY restart
        // after a process kill. Fall back to the phone-call type (not while-in-use restricted)
        // so the promotion, and the empty-metadata guard below, run instead of crash-looping
        // the restart.
        try {
            startForegroundServiceCompat(
                this,
                ActiveCallNotificationBuilder.NOTIFICATION_ID,
                notification,
                getForegroundServiceTypes(callsMetadata),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "onStartCommand: typed promotion rejected, falling back to phone-call type", e)
            startForegroundServiceCompat(
                this,
                ActiveCallNotificationBuilder.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        }

        if (callsMetadata.isEmpty()) {
            // Empty metadata means a START_STICKY restart delivered a null intent after the
            // process was killed. NotificationManager tracks active calls in its own static
            // list, so nothing will ever stop this orphaned instance - an ongoing notification
            // left here would be undismissable until the user force-stops the app.
            Log.w(TAG, "onStartCommand: no calls metadata (null-intent restart), stopping self")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun hungUpCall() {
        val call = callsMetadata.firstOrNull()
        if (call != null) {
            CallkeepCore.instance.startHungUpCall(call)
        } else {
            // Hang up tapped on a notification with no known calls (re-posted by a null-intent
            // restart). tearDownService only tears down the connection services and does not
            // stop this one, so the notification has to be removed here.
            Log.w(TAG, "hungUpCall: no calls metadata, tearing down and stopping self")
            CallkeepCore.instance.tearDownService()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun getForegroundServiceTypes(callsMetadata: List<CallMetadata>): Int? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val hasVideo = callsMetadata.any { it.hasVideo ?: false }
                val hasCameraPermission = PermissionsHelper(this).hasCameraPermission()
                val hasMicrophonePermission = PermissionsHelper(this).hasMicrophonePermission()
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL

                // CRITICAL: Explicitly register MICROPHONE type if permission is granted.
                // Do NOT condition this on 'audioDevice' or output state.
                // Strict OS implementations (e.g., Samsung OneUI) will block microphone access
                // when the screen is off if this type is missing, even for active calls.
                if (hasMicrophonePermission) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }

                if (hasVideo && hasCameraPermission) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }

                types
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }

            else -> {
                null
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ActiveCallService"
    }
}
