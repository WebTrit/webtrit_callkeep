package com.webtrit.callkeep.services.incomming_call

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
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

    private lateinit var incomingCallHandler: IncomingCallHandler

    lateinit var callLifecycleHandler: CallLifecycleHandler
    lateinit var isolateHandler: FlutterIsolateHandler

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)

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

        CommunicateServiceDispatcher.registerService(this::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)
        val action = intent?.action

        Log.d(TAG, "onStartCommand: $action, $metadata")

        return when (action) {
            NotificationAction.Answer.action -> handleAnswer(metadata!!)
            NotificationAction.Hangup.action -> handleHangup(metadata!!)
            PushNotificationServiceEnums.LAUNCH.name -> handleLaunch(metadata!!)
            ConnectionReport.MissedCall.name -> handleMissedCall(metadata!!)
            else -> handleUnknownAction(action)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isolateHandler?.cleanup()
        CommunicateServiceDispatcher.unregisterService(this::class.java)
        super.onDestroy()
    }

    fun establishFlutterCommunication(
        serviceApi: PDelegateBackgroundServiceFlutterApi, registerApi: PDelegateBackgroundRegisterFlutterApi
    ) {
        callLifecycleHandler.apply {
            flutterApi = DefaultFlutterIsolateCommunicator(this@IncomingCallService, serviceApi, registerApi)
        }

        incomingCallHandler.apply {
        }
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

    private fun handleLaunch(metadata: CallMetadata): Int {
        incomingCallHandler.handle(metadata)
        return START_STICKY
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

        private fun communicate(context: Context, metadata: Bundle?) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = PushNotificationServiceEnums.LAUNCH.name
                metadata?.let { putExtras(it) }
            }
            context.startForegroundService(intent)
        }

        fun start(context: Context, metadata: CallMetadata) = communicate(context, metadata.toBundle())

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) = context.stopService(Intent(context, IncomingCallService::class.java))
    }
}

enum class PushNotificationServiceEnums {
    LAUNCH
}

enum class DeclineSource {
    USER, SERVER
}
