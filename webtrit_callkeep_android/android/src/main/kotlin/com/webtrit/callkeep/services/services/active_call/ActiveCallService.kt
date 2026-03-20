package com.webtrit.callkeep.services.services.active_call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.parcelableArrayList
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.ActiveCallNotificationBuilder
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService

class ActiveCallService : Service() {
    private val activeCallNotificationBuilder = ActiveCallNotificationBuilder()
    private var callsMetadata = mutableListOf<CallMetadata>()

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
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

        startForegroundServiceCompat(
            this,
            ActiveCallNotificationBuilder.NOTIFICATION_ID,
            notification,
            getForegroundServiceTypes(callsMetadata),
        )

        // TODO: maybe FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK is needed as well

        return START_STICKY
    }

    private fun hungUpCall() =
        callsMetadata.firstOrNull()?.let {
            PhoneConnectionService.startHungUpCall(baseContext, it)
        } ?: PhoneConnectionService.tearDown(baseContext)

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
}
