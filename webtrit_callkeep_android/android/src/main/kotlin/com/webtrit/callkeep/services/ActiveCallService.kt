package com.webtrit.callkeep.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.ActiveCallNotificationBuilder

class ActiveCallService : Service() {
    private var activeCallNotificationBuilder = ActiveCallNotificationBuilder(ContextHolder.context)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableArrayListExtra("metadata", Bundle::class.java)
        } else {
            intent?.getParcelableArrayListExtra<Bundle>("metadata");
        }
        val callsMetadata = bundle?.map { CallMetadata.fromBundle(it) } ?: emptyList()
        
        activeCallNotificationBuilder.setCallsMetaData(callsMetadata)
        val notification = activeCallNotificationBuilder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (callsMetadata.any { it.hasVideo }) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }

            ServiceCompat.startForeground(
                this,
                ActiveCallNotificationBuilder.ACTIVE_CALL_NOTIFICATION_ID,
                notification,
                types,
            )
        } else {
            startForeground(
                ActiveCallNotificationBuilder.ACTIVE_CALL_NOTIFICATION_ID, notification
            )
        }

        // TODO: maybe FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK is needed as well

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
