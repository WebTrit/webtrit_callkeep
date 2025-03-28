package com.webtrit.callkeep.services.incomming_call

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.ConnectionReport
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.SignalingIsolateService
import com.webtrit.callkeep.services.dispatchers.CommunicateServiceDispatcher
import com.webtrit.callkeep.services.helpers.DefaultIsolateLaunchPolicy

@Keep
class IncomingCallService : Service() {
    private val incomingCallNotificationBuilder by lazy { IncomingCallNotificationBuilder() }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val stopTimeoutRunnable = Runnable {
        Log.w(TAG, "Service stop timeout ($SERVICE_TIMEOUT_MS ms) reached. Stopping forcefully.")
        stopSelf()
    }

    private lateinit var incomingCallHandler: IncomingCallHandler

    lateinit var callLifecycleHandler: CallLifecycleHandler
    lateinit var isolateHandler: FlutterIsolateHandler

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        CommunicateServiceDispatcher.registerService(this::class.java)

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

        if (startId == 0 && action != PushNotificationServiceEnums.LAUNCH.name) {
            Log.w(TAG, "Service was not properly started (startId=0), stopping to avoid crash")
            stopSelf()
            return START_NOT_STICKY
        }

        return when (action) {
            NotificationAction.Answer.action -> handleAnswer(metadata!!)
            NotificationAction.Hangup.action -> handleHangup(metadata!!)
            PushNotificationServiceEnums.LAUNCH.name -> handleLaunch(metadata!!)
            PushNotificationServiceEnums.RELEASE.name -> performDeclineCall(metadata)
            ConnectionReport.MissedCall.name -> handleMissedCall(metadata!!)
            else -> handleUnknownAction(action)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        CommunicateServiceDispatcher.unregisterService(this::class.java)
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

        incomingCallHandler.apply {}
    }

    private fun handleAnswer(metadata: CallMetadata): Int {
        if (SignalingIsolateService.isRunning) {
            SignalingIsolateService.answerCall(baseContext, metadata)
        } else {
            callLifecycleHandler.answerCall(metadata)
        }
        return START_NOT_STICKY
    }

    private fun handleHangup(metadata: CallMetadata): Int {
        if (SignalingIsolateService.isRunning) {
            SignalingIsolateService.endCall(baseContext, metadata)
        } else {
            callLifecycleHandler.terminateCall(metadata, DeclineSource.USER)
        }
        return START_NOT_STICKY
    }

    // Starts the service with the LAUNCH action, cancelling any existing timeout,
    // since this is a new incoming call
    private fun handleLaunch(metadata: CallMetadata): Int {
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        incomingCallHandler.handle(metadata)
        return START_STICKY
    }


    // Starts the service with the RELEASE action and schedules a timeout,
    // in case the Flutter isolate doesn't stop the service correctly
    private fun performDeclineCall(metadata: CallMetadata?): Int {
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.postDelayed(stopTimeoutRunnable, SERVICE_TIMEOUT_MS)
        if (metadata != null) callLifecycleHandler.performEndCall(metadata) else callLifecycleHandler.release()
        return START_NOT_STICKY
    }

    private fun handleMissedCall(metadata: CallMetadata): Int {
        callLifecycleHandler.handleMissedCall(metadata)
        return START_NOT_STICKY
    }

    private fun handleUnknownAction(action: String?): Int {
        Log.e(TAG, "Unknown or missing intent action: $action")
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "IncomingCallService"

        private const val SERVICE_TIMEOUT_MS = 2_000L

        private fun communicate(context: Context, action: PushNotificationServiceEnums, metadata: Bundle?) {
            Log.d(TAG, "Communicate with action: $action, metadata: $metadata")

            val intent = Intent(context, IncomingCallService::class.java).apply {
                this.action = action.name
                metadata?.let { putExtras(it) }
            }

            if (action == PushNotificationServiceEnums.LAUNCH) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }


        fun start(context: Context, metadata: CallMetadata) {
            communicate(context, PushNotificationServiceEnums.LAUNCH, metadata.toBundle())
        }

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            communicate(context, PushNotificationServiceEnums.RELEASE, null)
        }
    }
}

enum class PushNotificationServiceEnums {
    LAUNCH, RELEASE
}

enum class DeclineSource {
    USER, SERVER
}
