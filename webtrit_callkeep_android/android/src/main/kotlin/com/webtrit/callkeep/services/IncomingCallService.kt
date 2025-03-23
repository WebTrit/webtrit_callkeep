package com.webtrit.callkeep.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Keep
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.dispatchers.IncomingCallEventDispatcher
import com.webtrit.callkeep.services.helpers.IsolateSelector
import com.webtrit.callkeep.services.helpers.IsolateType

/**
 * Service that handles incoming calls.
 *
 * This service is started by the incoming call notification and manages the incoming call lifecycle.
 * It starts the Flutter background isolate and communicates with the main isolate (Activity) or notification incoming isolate to handle the call.
 *
 * If the app is in the background, closed, or minimized, the service starts the Flutter background isolate and communicates with the notification incoming isolate to handle the call. If signaling is connected, it is provided by the main isolate or app resumed using the main isolate.
 */
@Keep
class IncomingCallService : Service() {
    private val incomingCallNotificationBuilder by lazy { IncomingCallNotificationBuilder() }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)

        if (!PermissionsHelper(applicationContext).hasNotificationPermission()) {
            Log.e(TAG, "Notification permission not granted")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        PushNotificationService.stop(applicationContext)

        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val metadata = intent?.extras?.let { CallMetadata.fromBundle(it) }

        when (intent?.action) {
            // Notification actions
            NotificationAction.Answer.action -> IncomingCallEventDispatcher.answer(baseContext, metadata!!)
            NotificationAction.Hangup.action -> IncomingCallEventDispatcher.hungUp(baseContext, metadata!!)
            else -> Log.e(TAG, "Missing or unknown intent action")
        }

        startForegroundServiceCompat(
            this,
            IncomingCallNotificationBuilder.INCOMING_CALL_NOTIFICATION_ID,
            incomingCallNotificationBuilder.apply { setCallMetaData(metadata!!) }.build()
        )


        val isolate = IsolateSelector.getIsolateType()
        val signalingServiceRunning = SignalingService.isRunning

        // Launch push notifications callbacks and handling only if signaling service is not running
        if (isolate == IsolateType.BACKGROUND && !signalingServiceRunning) {
            Log.d(TAG, "Launching isolate: $metadata")
            PushNotificationService.start(applicationContext, metadata!!)
        } else {
            Log.d(TAG, "Skipped launching isolate: $metadata")
        }

        return START_STICKY
    }


    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "IncomingCallService"

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context, metadata: Bundle?) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                metadata?.let { putExtras(it) }
            }
            context.startForegroundService(intent)

        }

        fun start(context: Context, metadata: CallMetadata) = communicate(context, metadata.toBundle())

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) = context.stopService(Intent(context, IncomingCallService::class.java))
    }
}
