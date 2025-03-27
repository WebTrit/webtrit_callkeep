package com.webtrit.callkeep.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Keep
import com.webtrit.callkeep.PCallkeepPushNotificationSyncStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHostBackgroundPushNotificationIsolateApi
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.FlutterEngineHelper
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.ConnectionReport
import com.webtrit.callkeep.services.dispatchers.CommunicateServiceDispatcher
import com.webtrit.callkeep.services.dispatchers.SignalingStatusDispatcher
import com.webtrit.callkeep.services.helpers.IsolateSelector
import com.webtrit.callkeep.services.helpers.IsolateType
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

enum class PushNotificationServiceEnums {
    LAUNCH, ANSWER, HANGUP;
}

enum class DeclineSource {
    USER, SERVER
}

/**
 * Service that handles incoming calls.
 *
 * This service is started by the incoming call notification and manages the incoming call lifecycle.
 * It starts the Flutter background isolate and communicates with the main isolate (Activity) or notification incoming isolate to handle the call.
 *
 * If the app is in the background, closed, or minimized, the service starts the Flutter background isolate and communicates with the notification incoming isolate to handle the call. If signaling is connected, it is provided by the main isolate or app resumed using the main isolate.
 */
@Keep
class PushNotificationIsolateService : Service(), PHostBackgroundPushNotificationIsolateApi {
    private var flutterEngineHelper: FlutterEngineHelper? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var _isolateCalkeepFlutterApi: PDelegateBackgroundServiceFlutterApi? = null
    var isolateCalkeepFlutterApi: PDelegateBackgroundServiceFlutterApi?
        get() = _isolateCalkeepFlutterApi
        set(value) {
            _isolateCalkeepFlutterApi = value
        }

    private var _isolatePushNotificationFlutterApi: PDelegateBackgroundRegisterFlutterApi? = null
    var isolatePushNotificationFlutterApi: PDelegateBackgroundRegisterFlutterApi?
        get() = _isolatePushNotificationFlutterApi
        set(value) {
            _isolatePushNotificationFlutterApi = value
        }

    fun acquireWakeLock(context: Context) {
        if (wakeLock == null) {
            val mgr = context.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.webtrit.callkeep:IncomingCallService.Lock")
            wakeLock?.setReferenceCounted(false)
        }
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
    }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)

        // Subscribe to connection service events for handling missed call event
        CommunicateServiceDispatcher.registerService(this::class.java)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        isRunning = false

        flutterEngineHelper?.detachAndDestroyEngine()
        flutterEngineHelper = null

        CommunicateServiceDispatcher.unregisterService(this::class.java)

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val extras = intent?.extras
        val metadata = extras?.let(CallMetadata::fromBundleOrNull)

        return when (action) {
            PushNotificationServiceEnums.LAUNCH.name -> {
                handleIncomingCallLaunch()
                START_STICKY
            }

            PushNotificationServiceEnums.ANSWER.name -> {
                answerCall(metadata!!)
                START_NOT_STICKY
            }

            PushNotificationServiceEnums.HANGUP.name -> {
                terminateCall(metadata!!, DeclineSource.USER)
                START_NOT_STICKY
            }

            ConnectionReport.MissedCall.name -> {
                handleMissedCall(metadata!!)
                START_NOT_STICKY
            }

            else -> {
                Log.e(TAG, "Unknown or missing intent action: $action")
                START_NOT_STICKY
            }
        }
    }

    /**
     * Handles the launch of an incoming call.
     *
     * Starts the foreground service with the incoming call notification and
     * checks the signaling status. If the signaling status is not connected or connecting,
     * it triggers background synchronization.
     *
     * @param metadata The metadata of the incoming call.
     */
    private fun handleIncomingCallLaunch() {
        val isolate = IsolateSelector.getIsolateType()
        val signalingIsolateServiceRunning = SignalingIsolateService.isRunning
        val launchBackgroundIsolateEvenIfAppIsOpen =
            StorageDelegate.IncomingCallService.isLaunchBackgroundIsolateEvenIfAppIsOpen(baseContext)

        // Launch push notifications callbacks and handling only if signaling service is not running
        if (launchBackgroundIsolateEvenIfAppIsOpen || (isolate == IsolateType.BACKGROUND && !signalingIsolateServiceRunning)) {
            Log.d(TAG, "Launching isolate:")

            startIncomingCallIsolate()

            isolatePushNotificationFlutterApi?.onNotificationSync(
                StorageDelegate.IncomingCallService.getOnNotificationSync(applicationContext),
                PCallkeepPushNotificationSyncStatus.SYNCHRONIZE_CALL_STATUS
            ) { response ->
                response.onSuccess {
                    Log.d(TAG, "Background synchronization started successfully")
                }
                response.onFailure {
                    Log.e(TAG, "Failed to synchronize background call status")
                }
            }
        } else {
            Log.d(TAG, "Skipped launching isolate")
            stopSelf();
        }
    }

    /**
     * Records a missed call event when the main isolate is not running.
     *
     * @param extras The missed call information.
     */
    private fun handleMissedCall(metadata: CallMetadata) {
        Log.d(TAG, "handleMissedCall: $metadata")

        isolateCalkeepFlutterApi?.endCallReceived(
            metadata.callId,
            metadata.number,
            metadata.hasVideo,
            metadata.createdTime ?: System.currentTimeMillis(),
            null,
            System.currentTimeMillis()
        ) { response ->
            response.onSuccess {
                // Do not directly invoke PhoneConnectionService.startHungUpCall here. Instead, wait for the signaling
                // response (RELEASE_RESOURCES). After successful signaling, DeclineSource.USER will be triggered from Flutter.
                Log.d(TAG, "handleMissedCall success: $it")
            }
            response.onFailure {
                // If signaling fails, directly end the call and close the isolate.
                Log.e(TAG, "handleMissedCall failure: $it")
                PhoneConnectionService.startHungUpCall(baseContext, metadata)
                stopSelf()
            }
        }
    }

    /**
     * Runs the service and starts the Flutter background isolate.
     */
    @SuppressLint("WakelockTimeout")
    private fun startIncomingCallIsolate() {
        val callbackDispatcher = StorageDelegate.IncomingCallService.getCallbackDispatcher(applicationContext)

        acquireWakeLock(baseContext)

        if (flutterEngineHelper == null) {
            flutterEngineHelper = FlutterEngineHelper(this, callbackDispatcher, this)
        }

        flutterEngineHelper?.startOrAttachEngine()

        isRunning = true
    }

    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        terminateCall(CallMetadata(callId = callId), DeclineSource.SERVER)
        callback(Result.success(Unit))
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        isolatePushNotificationFlutterApi?.onNotificationSync(
            StorageDelegate.IncomingCallService.getOnNotificationSync(applicationContext),
            PCallkeepPushNotificationSyncStatus.RELEASE_RESOURCES
        ) { response ->
            response.onSuccess {
                com.webtrit.callkeep.common.Log.d(TAG, "onWakeUpBackgroundHandler: $it")
                PhoneConnectionService.tearDown(baseContext)
            }
            response.onFailure {
                com.webtrit.callkeep.common.Log.e(TAG, "onWakeUpBackgroundHandler: $it")
            }
        }
    }

    /**
     * Ends the call based on the current signaling status and the source of the decline.
     *
     * If the signaling status is CONNECT or CONNECTING, the call hang-up is handled in the main isolate (Activity).
     * Otherwise, the call is handled in the background isolate based on the decline source.
     *
     * @param metadata The metadata of the call to be ended.
     * @param declineSource The source of the decline, either USER or SERVER.
     */
    private fun terminateCall(metadata: CallMetadata, declineSource: DeclineSource) {
        when (IsolateSelector.getIsolateType()) {
            // If the signaling status is CONNECT or CONNECTING, handle the call hang-up in the main isolate (Activity)
            IsolateType.MAIN -> PhoneConnectionService.startHungUpCall(baseContext, metadata)
            // If the app is in the background, closed, or minimized, handle the call in the background isolate
            IsolateType.BACKGROUND -> when (declineSource) {
                DeclineSource.USER -> handleUserDecline(metadata)
                DeclineSource.SERVER -> handleServerDecline(metadata)
            }
        }
    }

    private fun answerCall(metadata: CallMetadata) {
        Log.d(TAG, "Answering call: $metadata")
        when (IsolateSelector.getIsolateType()) {
            // If the signaling status is CONNECT or CONNECTING, handle the call hang-up in the main isolate (Activity)
            IsolateType.MAIN -> PhoneConnectionService.startAnswerCall(baseContext, metadata)
            IsolateType.BACKGROUND -> _isolateCalkeepFlutterApi?.performAnswerCall(metadata.callId) { response ->
                response.onSuccess {
                    PhoneConnectionService.startAnswerCall(baseContext, metadata)
                }
                response.onFailure {
                    PhoneConnectionService.tearDown(baseContext)
                }
            }
        }
    }

    /**
     * Handles the user decline of an incoming call.
     *
     * If the Flutter engine is ready, it sends an end-call event via the Flutter API.
     * If the signaling fails, it directly ends the call and stops the service.
     * If the Flutter engine is not ready, it launches the isolate and sends an end-call event.
     *
     * @param metadata The metadata of the call to be ended.
     */
    private fun handleUserDecline(metadata: CallMetadata) {
        if (isFlutterEngineReady) {
            // Check if the isolate is attached to the engine. If an incoming call started from the activity,
            // the isolate will not launch because it has already been handled by the main isolate.
            isolateCalkeepFlutterApi?.performEndCall(metadata.callId) { response ->
                response.onSuccess {
                    // Do not directly invoke PhoneConnectionService.startHungUpCall here. Instead, wait for the signaling
                    // response (RELEASE_RESOURCES). After successful signaling, DeclineSource.USER will be triggered from Flutter.
                    Log.d(TAG, "Call end successfully sent via signaling")
                }
                response.onFailure {
                    // If signaling fails, directly end the call and close the isolate.
                    Log.e(TAG, "Call end signaling failed: $it")
                    PhoneConnectionService.startHungUpCall(baseContext, metadata)
                    stopSelf()
                }
            }
        } else {
            // If the incoming call started from the main isolate and the user minimized the app, launch the isolate
            // and send an end-call event.
            isolatePushNotificationFlutterApi?.onNotificationSync(
                StorageDelegate.IncomingCallService.getOnNotificationSync(applicationContext),
                PCallkeepPushNotificationSyncStatus.SYNCHRONIZE_CALL_STATUS
            ) { response ->
                response.onSuccess {
                    isolateCalkeepFlutterApi?.performEndCall(metadata.callId) {
                        Log.d(TAG, "Call end successfully sent via signaling")
                        // Do not directly invoke PhoneConnectionService.startHungUpCall here. Instead, wait for the signaling
                        // response (RELEASE_RESOURCES). After successful signaling, DeclineSource.USER will be triggered from Flutter.
                    }
                }
                response.onFailure {
                    // If signaling fails, directly end the call and close the isolate.
                    Log.e(TAG, "Call end signaling failed: $it")
                    PhoneConnectionService.startHungUpCall(baseContext, metadata)
                    stopSelf()
                }
            }
        }
    }

    /**
     * Handles the server decline of an incoming call.
     *
     * This method is called when the server sends a decline event, the caller cancels the call,
     * or the decline is confirmed by the user.
     *
     * It sends an event to clean up background isolate resources and close the signaling connection.
     *
     * @param metadata The metadata of the call to be ended.
     */
    private fun handleServerDecline(metadata: CallMetadata) {
        // Send event to clean up background isolate resources and close signaling connection
        isolatePushNotificationFlutterApi?.onNotificationSync(
            StorageDelegate.IncomingCallService.getOnNotificationSync(applicationContext),
            PCallkeepPushNotificationSyncStatus.RELEASE_RESOURCES
        ) { response ->
            PhoneConnectionService.startHungUpCall(baseContext, metadata)
        }
    }

    private val isFlutterEngineReady: Boolean
        get() = flutterEngineHelper?.isEngineAttached == true

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PushNotificationIsolateService"

        @JvmStatic
        var isRunning = false

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context, action: PushNotificationServiceEnums, metadata: Bundle?) {
            Log.d(TAG, "Communicating with service: $action")
            val intent = Intent(context, PushNotificationIsolateService::class.java).apply {
                this.action = action.name
                metadata?.let { putExtras(it) }

            }
            context.startService(intent)
        }

        fun start(context: Context, metadata: CallMetadata) =
            communicate(context, PushNotificationServiceEnums.LAUNCH, metadata.toBundle())

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) = context.stopService(Intent(context, PushNotificationIsolateService::class.java))

        fun answer(context: Context, metadata: CallMetadata) =
            communicate(context, PushNotificationServiceEnums.ANSWER, metadata.toBundle())

        fun hangup(context: Context, metadata: CallMetadata) =
            communicate(context, PushNotificationServiceEnums.HANGUP, metadata.toBundle())
    }
}
