package com.webtrit.callkeep.services

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostBackgroundSignalingIsolateApi
import com.webtrit.callkeep.common.*
import com.webtrit.callkeep.models.*
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder
import com.webtrit.callkeep.services.dispatchers.SignalingStatusDispatcher
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService
import com.webtrit.callkeep.services.workers.SignalingServiceBootWorker

/**
 * A foreground service that manages the call state and Flutter background isolate.
 *
 * Maintains an open socket connection with the server to receive incoming calls and communicate with the Flutter background isolate.
 * Triggers incoming calls, ends calls, ends all calls, and handles lifecycle events.
 */
@Keep
class SignalingIsolateService : Service(), PHostBackgroundSignalingIsolateApi {
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

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        // Subscribe to connection service events for handling missed call event
        SignalingStatusDispatcher.registerService(this::class.java)

        notificationBuilder = ForegroundCallNotificationBuilder()

        startForegroundService()

        val callbackDispatcher = StorageDelegate.SignalingService.getCallbackDispatcher(applicationContext)
        flutterEngineHelper = FlutterEngineHelper(applicationContext, callbackDispatcher, this)

        isRunning = true
    }

    override fun onDestroy() {
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

        SignalingStatusDispatcher.unregisterService(this::class.java)

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
            SignalingStatusDispatcher.ACTION_STATUS_CHANGED -> {
                synchronizeSignalingIsolate(
                    ActivityHolder.getActivityState(),
                    SignalingStatus.fromBundle(intent.extras)
                )
                return START_STICKY
            }

            ForegroundCallServiceEnums.CHANGE_LIFECYCLE.action -> {
                synchronizeSignalingIsolate(
                    extras?.serializableCompat<Lifecycle.Event>(PARAM_CHANGE_LIFECYCLE_EVENT)!!,
                    SignalingStatusDispatcher.currentStatus
                )
                return START_STICKY
            }

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

            // Connection service events
            ConnectionReport.MissedCall.name -> {
                handleMissedCall(metadata!!)
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
        private const val PARAM_CHANGE_LIFECYCLE_EVENT = "PARAM_CHANGE_LIFECYCLE_EVENT"

        var isRunning = false

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context, action: ForegroundCallServiceEnums?, metadata: Bundle?) {
            val intent = Intent(context, SignalingIsolateService::class.java).apply {
                this.action = action?.action
                metadata?.let { putExtras(it) }
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot start service: ${e.message}")
            }
        }

        fun start(context: Context) = communicate(context, null, null)

        fun changeLifecycle(context: Context, event: Lifecycle.Event) = communicate(
            context,
            ForegroundCallServiceEnums.CHANGE_LIFECYCLE,
            Bundle().apply { putSerializable(PARAM_CHANGE_LIFECYCLE_EVENT, event) })

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            SignalingServiceBootWorker.Companion.remove(context)

            context.stopService(Intent(context, SignalingIsolateService::class.java))
        }

        fun endCall(context: Context, callMetadata: CallMetadata) =
            communicate(context, ForegroundCallServiceEnums.DECLINE, callMetadata.toBundle())

        fun answerCall(context: Context, callMetadata: CallMetadata) =
            communicate(context, ForegroundCallServiceEnums.ANSWER, callMetadata.toBundle())

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
    CHANGE_LIFECYCLE, ANSWER, DECLINE;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}
