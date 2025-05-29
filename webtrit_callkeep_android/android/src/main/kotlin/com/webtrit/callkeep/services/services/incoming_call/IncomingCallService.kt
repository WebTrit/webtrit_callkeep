package com.webtrit.callkeep.services.services.incoming_call

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.common.DefaultIsolateLaunchPolicy
import com.webtrit.callkeep.services.services.incoming_call.handlers.CallLifecycleHandler
import com.webtrit.callkeep.services.services.incoming_call.handlers.FlutterIsolateHandler
import com.webtrit.callkeep.services.services.incoming_call.handlers.IncomingCallHandler

@Keep
class IncomingCallService : Service() {
    private val incomingCallNotificationBuilder by lazy { IncomingCallNotificationBuilder() }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val stopTimeoutRunnable = Runnable {
        Log.w(TAG, "Service stop timeout ($SERVICE_TIMEOUT_MS ms) reached. Stopping forcefully.")
        stopSelf()
    }

    private lateinit var incomingCallHandler: IncomingCallHandler
    private lateinit var isolateHandler: FlutterIsolateHandler
    private lateinit var callLifecycleHandler: CallLifecycleHandler
    fun getCallLifecycleHandler(): CallLifecycleHandler = callLifecycleHandler

    private val connectionService = listOf(
        ConnectionPerform.AnswerCall,
        ConnectionPerform.DeclineCall,
        ConnectionPerform.HungUp,
        ConnectionPerform.MissedCall
    )

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)

            when (intent?.action) {
                // Listen connection service actions (and try to notify isolate if it background)
                ConnectionPerform.AnswerCall.name -> performAnswerCall(metadata!!)
                ConnectionPerform.DeclineCall.name -> performDeclineCall(metadata!!)
                ConnectionPerform.HungUp.name -> performDeclineCall(metadata!!)
                ConnectionPerform.MissedCall.name -> performMissedCall(metadata!!)

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setRunning(true)
        ContextHolder.init(applicationContext)
        // Register the service to receive connection service perform events
        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            connectionService, this, connectionServicePerformReceiver
        )

        isolateHandler = FlutterIsolateHandler(this@IncomingCallService, this@IncomingCallService) {
            callLifecycleHandler.flutterApi?.syncPushIsolate(onSuccess = {}, onFailure = {})
        }

        incomingCallHandler = IncomingCallHandler(
            service = this,
            notificationBuilder = incomingCallNotificationBuilder,
            isolateLaunchPolicy = DefaultIsolateLaunchPolicy(this),
            isolateInitializer = isolateHandler,
        )

        callLifecycleHandler = CallLifecycleHandler(
            connectionController = DefaultCallConnectionController(baseContext),
            stopService = { stopSelf() },
            isolateHandler = isolateHandler,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)
        val action = intent?.action

        Log.d(TAG, "onStartCommand: $action, $metadata")

        if (startId == 0 && action != PushNotificationServiceEnums.IC_INITIALIZE.name) {
            Log.w(TAG, "Service was not properly started (startId=0), stopping to avoid crash")
            stopSelf()
            return START_NOT_STICKY
        }

        return when (action) {
            // Listen foreground service actions
            PushNotificationServiceEnums.IC_INITIALIZE.name -> handleLaunch(metadata!!)
            IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name -> handleRelease(answered = false)
            IncomingCallRelease.IC_RELEASE_WITH_ANSWER.name -> handleRelease(answered = true)

            // Listen push notification actions (Only notify connection service)
            NotificationAction.Answer.action -> reportAnswerToConnectionService(metadata!!)
            NotificationAction.Decline.action -> reportHungUpToConnectionService(metadata!!)

            else -> handleUnknownAction(action)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        setRunning(false)
        // Unregister the service from receiving connection service perform events
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(this, connectionServicePerformReceiver)

        stopForeground(STOP_FOREGROUND_REMOVE)
        isolateHandler.cleanup()
        super.onDestroy()
    }

    fun establishFlutterCommunication(
        serviceApi: PDelegateBackgroundServiceFlutterApi, registerApi: PDelegateBackgroundRegisterFlutterApi
    ) {
        callLifecycleHandler.apply {
            flutterApi = DefaultFlutterIsolateCommunicator(this@IncomingCallService, serviceApi, registerApi)
        }
    }

    private fun reportAnswerToConnectionService(metadata: CallMetadata): Int {
        callLifecycleHandler.reportAnswerToConnectionService(metadata)
        return START_NOT_STICKY
    }

    private fun reportHungUpToConnectionService(metadata: CallMetadata): Int {
        callLifecycleHandler.reportDeclineToConnectionService(metadata)
        return START_NOT_STICKY
    }

    private fun performAnswerCall(metadata: CallMetadata): Int {
        callLifecycleHandler.performAnswerCall(metadata)
        return START_STICKY
    }

    private fun performMissedCall(metadata: CallMetadata): Int {
        callLifecycleHandler.handleMissedCall(metadata)
        return START_NOT_STICKY
    }

    // Starts the service with the RELEASE action and schedules a timeout,
    // in case the Flutter isolate doesn't stop the service correctly
    private fun performDeclineCall(metadata: CallMetadata): Int {
        callLifecycleHandler.performEndCall(metadata)
        return START_NOT_STICKY
    }

    // Launches the service with the LAUNCH action and cancels the timeout
    private fun handleLaunch(metadata: CallMetadata): Int {
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        incomingCallHandler.handle(metadata)
        return START_STICKY
    }

    // Handles the RELEASE action and cancels the timeout
    private fun handleRelease(answered: Boolean = false): Int {
        if (!answered) incomingCallHandler.releaseIncomingCallNotification()
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.postDelayed(stopTimeoutRunnable, SERVICE_TIMEOUT_MS)
        callLifecycleHandler.release()
        return START_NOT_STICKY
    }

    private fun handleUnknownAction(action: String?): Int {
        Log.e(TAG, "Unknown or missing intent action: $action")
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "IncomingCallService"

        private const val SERVICE_TIMEOUT_MS = 2_000L

        @Volatile
        private var isRunning = false
        
        private fun setRunning(running: Boolean) {
            isRunning = running
        }

        fun start(context: Context, metadata: CallMetadata) {
            context.startForegroundService(Intent(context, IncomingCallService::class.java).apply {
                this.action = PushNotificationServiceEnums.IC_INITIALIZE.name
                metadata.toBundle().let(::putExtras)
            })
        }

        // Method is invoked when the connection is disconnected and the incoming call can be released.
        // Stopping the service immediately would destroy the isolate, which can be critical if the signaling layer
        // still needs to be notified about the disconnection.
        // Instead, we initiate communication with the Flutter side and delay stopping the service to ensure a graceful shutdown.
        // During this time, the notification is replaced with a special "release" notification
        // using IncomingCallNotificationBuilder.buildReleaseNotification to inform the user that the call is being finalized.
        fun release(context: Context, type: IncomingCallRelease) {
            if (isRunning) {
                context.startService(Intent(context, IncomingCallService::class.java).apply { this.action = type.name })
            } else {
                Log.w(TAG, "Service is not running. Release action $type ignored.")
            }
        }
    }
}

enum class IncomingCallRelease {
    IC_RELEASE_WITH_ANSWER, IC_RELEASE_WITH_DECLINE;
}

enum class PushNotificationServiceEnums {
    IC_INITIALIZE;
}
