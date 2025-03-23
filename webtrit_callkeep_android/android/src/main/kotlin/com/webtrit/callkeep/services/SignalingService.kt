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
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostBackgroundSignalingIsolateApi
import com.webtrit.callkeep.common.*
import com.webtrit.callkeep.models.*
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService
import com.webtrit.callkeep.services.workers.SignalingServiceBootWorker

/**
 * A foreground service that manages the call state and Flutter background isolate.
 *
 * Maintains an open socket connection with the server to receive incoming calls and communicate with the Flutter background isolate.
 * Triggers incoming calls, ends calls, ends all calls, and handles lifecycle events.
 */
class SignalingService : Service(), PHostBackgroundSignalingIsolateApi {
    private lateinit var notificationBuilder: ForegroundCallNotificationBuilder
    private lateinit var flutterEngineHelper: FlutterEngineHelper

    private var _isolatePushNotificationFlutterApi: PDelegateBackgroundRegisterFlutterApi? = null
    var isolatePushNotificationFlutterApi: PDelegateBackgroundRegisterFlutterApi?
        get() = _isolatePushNotificationFlutterApi
        set(value) {
            _isolatePushNotificationFlutterApi = value
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

        notificationBuilder = ForegroundCallNotificationBuilder()

        startForegroundService()

        val callbackDispatcher = StorageDelegate.SignalingService.getCallbackDispatcher(applicationContext)
        flutterEngineHelper = FlutterEngineHelper(applicationContext, callbackDispatcher, this)
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
                ForegroundCallNotificationBuilder.FOREGROUND_CALL_NOTIFICATION_ID,
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
        return activeNotifications.any { it.id == ForegroundCallNotificationBuilder.FOREGROUND_CALL_NOTIFICATION_ID }
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

        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ForegroundCallServiceEnums.INIT.action
        val data = intent?.extras

        ensureNotification()

        when (action) {
            ForegroundCallServiceEnums.INIT.action -> runService()
            ForegroundCallServiceEnums.START.action -> wakeUp()
            ForegroundCallServiceEnums.STOP.action -> tearDown()
            ForegroundCallServiceEnums.CHANGE_LIFECYCLE.action -> changedLifecycleHandler(data)
            ForegroundCallServiceEnums.DECLINE.action -> PhoneConnectionService.startHungUpCall(
                baseContext,
                CallMetadata.fromBundle(data!!)
            )

            ForegroundCallServiceEnums.ANSWER.action -> PhoneConnectionService.startAnswerCall(
                baseContext,
                CallMetadata.fromBundle(data!!)
            )
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (StorageDelegate.SignalingService.isSignalingServiceEnabled(context = applicationContext)) {
            SignalingServiceBootWorker.Companion.enqueue(applicationContext, 1000)
        }
    }

    /**
     * Wakes up the service and sends a broadcast to synchronize call status.
     */
    private fun wakeUp() {
        runService()
        wakeUpBackgroundHandler()
    }

    /**
     * Tears down the service gracefully.
     */
    private fun tearDown() {
        SignalingServiceBootWorker.Companion.remove(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Runs the service and starts the Flutter background isolate.
     */
    @SuppressLint("WakelockTimeout")
    private fun runService() {
        Log.v(TAG, "Running service logic")
        getLock(applicationContext)?.acquire(10 * 60 * 1000L /*10 minutes*/)

        flutterEngineHelper.startOrAttachEngine()

        isRunning = true
    }

    private fun wakeUpBackgroundHandler() {
        Log.d(TAG, "onWakeUpBackgroundHandler")

        val lifecycle = ActivityHolder.getActivityState()
        val pLifecycle = lifecycle.toPCallkeepLifecycleType()

        val wakeUpHandler = StorageDelegate.SignalingService.getOnStartHandler(baseContext)

        _isolatePushNotificationFlutterApi?.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                pLifecycle,
            )
        ) { response ->
            response.onSuccess {
                Log.d(TAG, "onWakeUpBackgroundHandler: $it")
            }
            response.onFailure {
                Log.e(TAG, "onWakeUpBackgroundHandler: $it")
            }
        }
    }


    @Suppress("DEPRECATION")
    private fun changedLifecycleHandler(bundle: Bundle?) {
        val event = bundle?.getSerializable(PARAM_CHANGE_LIFECYCLE_EVENT) as Lifecycle.Event?

        Log.d(TAG, "changedLifecycleHandler event: $event")

        val lifecycle = (event ?: Lifecycle.Event.ON_ANY).toPCallkeepLifecycleType()
        val onChangedLifecycleHandler = StorageDelegate.SignalingService.getOnChangedLifecycleHandler(baseContext)

        _isolatePushNotificationFlutterApi?.onApplicationStatusChanged(
            onChangedLifecycleHandler, PCallkeepServiceStatus(
                lifecycle,
            )
        ) { response ->
            response.onSuccess {
                Log.d(TAG, "appChanged: $it")
            }
            response.onFailure {
                Log.e(TAG, "appChanged: $it")
            }
        }
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
        PhoneConnectionService.startIncomingCall(baseContext, metadata)

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
        private const val TAG = "ForegroundCallService"
        private const val PARAM_JSON_DATA = "PARAM_JSON_DATA"
        private const val PARAM_CHANGE_LIFECYCLE_EVENT = "PARAM_CHANGE_LIFECYCLE_EVENT"

        var isRunning = false

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context, action: ForegroundCallServiceEnums, metadata: Bundle?) {
            val intent = Intent(context, SignalingService::class.java).apply {
                this.action = action.action
                metadata?.let { putExtras(it) }
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot start service: ${e.message}", e)
            }
        }

        fun start(context: Context, data: String? = null) = communicate(
            context, ForegroundCallServiceEnums.START, data?.let { Bundle().apply { putString(PARAM_JSON_DATA, it) } })

        fun changeLifecycle(context: Context, event: Lifecycle.Event) = communicate(
            context,
            ForegroundCallServiceEnums.CHANGE_LIFECYCLE,
            Bundle().apply { putSerializable(PARAM_CHANGE_LIFECYCLE_EVENT, event) })

        @SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            SignalingServiceBootWorker.Companion.remove(context)
            context.stopService(Intent(context, SignalingService::class.java))
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
    INIT, START, STOP, CHANGE_LIFECYCLE, ANSWER, DECLINE;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}
