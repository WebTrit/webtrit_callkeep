package com.webtrit.callkeep.services.services.incoming_call

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toPCallkeepIncomingCallData
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
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
        CallLifecycleEvent.AnswerCall,
    )

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)

            when (intent?.action) {
                // Listen connection service actions (and try to notify isolate if it background)
                CallLifecycleEvent.AnswerCall.name -> performAnswerCall(metadata!!)
                // DeclineCall and HungUp are handled via IC_RELEASE_WITH_DECLINE intent
                // (triggered from PhoneConnection.onDisconnect → cancelIncomingNotification).
                // Handling them here as well would cause a double performEndCall: once from
                // handleRelease and once from this receiver, racing to tear down the WebSocket
                // before the SIP BYE is sent. The IC_RELEASE_WITH_DECLINE path is the single
                // authoritative source for decline teardown.
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setRunning(true)
        ContextHolder.init(applicationContext)

        Log.d(TAG, "IncomingCallService created")

        // Register the service to receive connection service perform events
        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            connectionService, this, connectionServicePerformReceiver
        )

        isolateHandler = FlutterIsolateHandler(this@IncomingCallService, this@IncomingCallService) {
            callLifecycleHandler.flutterApi?.syncPushIsolate(callLifecycleHandler.currentCallData, onSuccess = {}, onFailure = {})
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
        Log.d(TAG, "onDestroy called")

        setRunning(false)
        // Unregister the service from receiving connection service perform events
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(
            this, connectionServicePerformReceiver
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        isolateHandler.cleanup()
        super.onDestroy()
    }

    fun establishFlutterCommunication(
        serviceApi: PDelegateBackgroundServiceFlutterApi,
        registerApi: PDelegateBackgroundRegisterFlutterApi
    ) {
        callLifecycleHandler.apply {
            flutterApi =
                DefaultFlutterIsolateCommunicator(this@IncomingCallService, serviceApi, registerApi)
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

    // Launches the service with the LAUNCH action and cancels the timeout
    private fun handleLaunch(metadata: CallMetadata): Int {
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        callLifecycleHandler.currentCallData = metadata.toPCallkeepIncomingCallData()
        incomingCallHandler.handle(metadata)
        return START_STICKY
    }

    // Handles the RELEASE action and cancels the timeout
    private fun handleRelease(answered: Boolean = false): Int {
        incomingCallHandler.releaseIncomingCallNotification(answered)
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.postDelayed(stopTimeoutRunnable, SERVICE_TIMEOUT_MS)
        if (answered) {
            // The call was answered and then ended by the remote/local side.
            // The background isolate is no longer needed for signaling — the main process
            // takes over the active-call session. Release resources immediately.
            callLifecycleHandler.release()
        } else {
            // The call was declined or hung up before being answered.
            // The signaling layer (WebSocket) must send a SIP BYE/decline to the server
            // BEFORE the WebSocket is torn down.
            //
            // Calling release() here directly (the old behaviour) would close the WebSocket
            // immediately, racing with the SIP BYE that performEndCall needs to send.
            //
            // Fix: call performEndCall first; its onSuccess/onFailure callbacks call release(),
            // which fires releaseResources and closes the WebSocket only after BYE completes
            // (or fails). If flutterApi is null, performEndCall falls back to release() directly
            // so cleanup always runs. The stopTimeoutRunnable above is an additional safety net
            // in case the Flutter isolate never responds.
            val callId = callLifecycleHandler.currentCallData?.callId
            if (callId != null) {
                callLifecycleHandler.performEndCall(CallMetadata(callId = callId))
            } else {
                Log.w(TAG, "handleRelease: no currentCallData, falling back to release()")
                callLifecycleHandler.release()
            }
        }
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
            Log.d(TAG, "Starting IncomingCallService with metadata: $metadata")
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
            // Do NOT guard on isRunning here. isRunning is a static field set only in the
            // main-process JVM. After the :callkeep_core process split, PhoneConnection
            // (which runs in :callkeep_core) calls this method; in that JVM isRunning is
            // always false, so the guard would silently drop every cancel request and leave
            // the incoming-call notification frozen after answer/decline.
            //
            // Sending the intent unconditionally is safe:
            //  - If IncomingCallService IS running (main process) it receives the release action.
            //  - If it is NOT running, startService either fails silently (background start
            //    restrictions on API 26+) or starts the service briefly; handleRelease returns
            //    early because lastMetadata is null, and the 2-second stopSelf timeout fires.
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, IncomingCallService::class.java).apply { this.action = type.name }
                )
                Log.d(TAG, "Release action $type initiated.")
            }.onFailure { e ->
                Log.w(TAG, "Release action $type: startForegroundService failed: $e")
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
