package com.webtrit.callkeep.services.services.signaling

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostBackgroundSignalingIsolateApi
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.FlutterEngineHelper
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.fromBundle
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.common.toPCallkeepLifecycleType
import com.webtrit.callkeep.common.toPCallkeepSignalingStatus
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.SignalingStatus
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleBroadcaster
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.broadcaster.SignalingStatusBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.signaling.workers.SignalingServiceBootWorker

/**
 * A foreground service that manages the call state and Flutter background isolate.
 *
 * Maintains an open socket connection with the server to receive incoming calls and communicate with the Flutter background isolate.
 * Triggers incoming calls, ends calls, ends all calls, and handles lifecycle events.
 */
@Keep
class SignalingIsolateService : Service(), PHostBackgroundSignalingIsolateApi {
    private var latestSignalingStatus: SignalingStatus? = null
    private var latestLifecycleActivityEvent: Lifecycle.Event? = Lifecycle.Event.ON_DESTROY

    private lateinit var notificationBuilder: ForegroundCallNotificationBuilder
    private lateinit var flutterEngineHelper: FlutterEngineHelper

    private var _isolateSignalingFlutterApi: PDelegateBackgroundRegisterFlutterApi? = null
    var isolateSignalingFlutterApi: PDelegateBackgroundRegisterFlutterApi?
        get() = _isolateSignalingFlutterApi
        set(value) {
            _isolateSignalingFlutterApi = value
        }

    private var _isolateCalkeepFlutterApi: PDelegateBackgroundServiceFlutterApi? = null
    var isolateCalkeepFlutterApi: PDelegateBackgroundServiceFlutterApi?
        get() = _isolateCalkeepFlutterApi
        set(value) {
            _isolateCalkeepFlutterApi = value
        }

    private val signalingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            latestSignalingStatus = SignalingStatus.fromBundle(intent?.extras)
            synchronizeSignalingIsolate(latestLifecycleActivityEvent!!, latestSignalingStatus)

        }
    }

    private val lifecycleEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            latestLifecycleActivityEvent = Lifecycle.Event.fromBundle(intent?.extras)
            synchronizeSignalingIsolate(
                latestLifecycleActivityEvent!!, latestSignalingStatus
            )
        }
    }

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectionPerform.MissedCall.name -> {
                    intent.extras?.let { handleMissedCall(CallMetadata.fromBundle(it)) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        // Register the service to receive signaling status updates
        latestSignalingStatus = SignalingStatusBroadcaster.currentValue
        SignalingStatusBroadcaster.register(this, signalingStatusReceiver)

        // Register the service to receive lifecycle events
        latestLifecycleActivityEvent = ActivityLifecycleBroadcaster.currentValue
        ActivityLifecycleBroadcaster.register(this, lifecycleEventReceiver)


        // Register the service to receive connection service perform events
        registerReceiverCompat(
            connectionServicePerformReceiver,
            IntentFilter(ConnectionPerform.MissedCall.name)
        )

        notificationBuilder = ForegroundCallNotificationBuilder()

        startForegroundService()

        val callbackDispatcher = StorageDelegate.SignalingService.getCallbackDispatcher(applicationContext)
        flutterEngineHelper = FlutterEngineHelper(applicationContext, callbackDispatcher, this)

        isRunning = true
    }

    override fun onDestroy() {
        // Unregister the service from receiving signaling status updates
        SignalingStatusBroadcaster.unregister(this, signalingStatusReceiver)
        latestSignalingStatus = null

        // Unregister the service from receiving lifecycle events
        ActivityLifecycleBroadcaster.unregister(baseContext, lifecycleEventReceiver)
        latestLifecycleActivityEvent = null

        // Unregister the service from receiving connection service perform events
        unregisterReceiver(connectionServicePerformReceiver)


        if (StorageDelegate.SignalingService.isSignalingServiceEnabled(context = applicationContext)) {
            SignalingServiceBootWorker.Companion.enqueue(this)
        }

        getLock(applicationContext)?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        flutterEngineHelper.detachAndDestroyEngine()

        isRunning = false

        super.onDestroy()
    }

    /**
     * Starts the service in the foreground with a notification.
     */
    private fun startForegroundService() {
        Log.d(TAG, "Starting foreground service")
        notificationBuilder.setTitle(StorageDelegate.SignalingService.getNotificationTitle(applicationContext))
        notificationBuilder.setContent(StorageDelegate.SignalingService.getNotificationDescription(applicationContext))
        val notification = notificationBuilder.build()

        if (PermissionsHelper(baseContext).hasNotificationPermission()) {
            startForegroundServiceCompat(
                this,
                ForegroundCallNotificationBuilder.NOTIFICATION_ID,
                notification,
                if (SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else null
            )
        } else {
            stopSelf()
        }
    }

    /**
     * Ensures that the notification is visible. If not, it restarts the foreground service.
     */
    private fun ensureNotification() {
        if (!isNotificationVisible()) {
            Log.d(TAG, "Notification not visible, restarting foreground service")
            startForegroundService()
        }
    }

    /**
     * Checks if the notification is currently visible.
     */
    private fun isNotificationVisible(): Boolean {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        return activeNotifications.any { it.id == ForegroundCallNotificationBuilder.NOTIFICATION_ID }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val extras = intent?.extras
        val metadata = extras?.let(CallMetadata::fromBundleOrNull)

        ensureNotification()

        when (action) {
            ForegroundCallServiceEnums.DECLINE.action -> {
                PhoneConnectionService.startHungUpCall(
                    baseContext, metadata!!
                )
                return START_STICKY
            }

            ForegroundCallServiceEnums.ANSWER.action -> {
                PhoneConnectionService.startAnswerCall(
                    baseContext, metadata!!
                )
                return START_STICKY
            }
        }

        getLock(applicationContext)?.acquire(10 * 60 * 1000L /*10 minutes*/)

        flutterEngineHelper.startOrAttachEngine()

        return START_STICKY
    }

    /**
     * Records a missed call event when the main isolate is not running.
     *
     * @param metadata The missed call information.
     */
    private fun handleMissedCall(metadata: CallMetadata) {
        Log.d(TAG, "handleMissedCall: $metadata")

        isolateCalkeepFlutterApi?.performReceivedCall(
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (StorageDelegate.SignalingService.isSignalingServiceEnabled(context = applicationContext)) {
            SignalingServiceBootWorker.Companion.enqueue(applicationContext, 1000)
        }
    }

    @Suppress("DEPRECATION")
    private fun synchronizeSignalingIsolate(activityLifecycle: Lifecycle.Event, status: SignalingStatus?) {
        val wakeUpHandler = StorageDelegate.SignalingService.getOnSyncHandler(baseContext)

        println("SignalingIsolateService synchronizeSignalingIsolate wakeUpHandler: $status")
        _isolateSignalingFlutterApi?.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                activityLifecycle.toPCallkeepLifecycleType(), mainSignalingStatus = status?.toPCallkeepSignalingStatus()
            )
        ) { response -> }
    }

    override fun incomingCall(
        callId: String, handle: PHandle, displayName: String?, hasVideo: Boolean, callback: (Result<Unit>) -> Unit
    ) {
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(baseContext)

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath,
            createdTime = System.currentTimeMillis()
        )

        PhoneConnectionService.startIncomingCall(
            context = baseContext,
            metadata = metadata,
            onSuccess = { callback(Result.success(Unit)) },
            onError = { error -> callback(Result.failure(Exception("Incoming call failed with error: $error"))) })
    }

    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.startHungUpCall(baseContext, metadata)
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.tearDown(baseContext)
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SignalingIsolateService"

        var isRunning = false

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context) {
            val intent = Intent(context, SignalingIsolateService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot start service: ${e.message}")
            }
        }

        fun start(context: Context) = communicate(context)

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            SignalingServiceBootWorker.Companion.remove(context)

            context.stopService(Intent(context, SignalingIsolateService::class.java))
        }

        /**
         * Acquires a partial wake lock to keep the CPU running.
         */
        @Synchronized
        fun getLock(context: Context): PowerManager.WakeLock? {
            val mgr = context.getSystemService(POWER_SERVICE) as PowerManager
            val lockName = "com.webtrit.callkeep:ForegroundCallService.Lock"
            return mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName).apply {
                setReferenceCounted(false)
            }
        }
    }
}

enum class ForegroundCallServiceEnums {
    ANSWER, DECLINE;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}
