package com.webtrit.callkeep.services.callkeep.foreground

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostBackgroundServiceApi
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.background.BackgroundCallkeepApi
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.FlutterEngineHelper
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.helpers.toPCallkeepLifecycleType
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.CallPaths
import com.webtrit.callkeep.models.ForegroundCallServiceConfig
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder
import com.webtrit.callkeep.receivers.IncomingCallNotificationReceiver
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService
import java.util.concurrent.atomic.AtomicBoolean

class ForegroundCallService : Service(), PHostBackgroundServiceApi {
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

    private lateinit var connectionService: BackgroundCallkeepApi
    private lateinit var incomingCallReceiver: IncomingCallNotificationReceiver

    fun register() {
        incomingCallReceiver.registerReceiver()
        connectionService.register()
    }

    fun unregister() {
        try {
            connectionService.unregister()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        incomingCallReceiver.unregisterReceiver()
    }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)

        notificationBuilder = ForegroundCallNotificationBuilder()

        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)

        checkNotificationPermission()
        startForegroundService(config)

        val callbackDispatcher = StorageDelegate.getCallbackDispatcher(applicationContext)
        flutterEngineHelper = FlutterEngineHelper(applicationContext, callbackDispatcher, this)
    }

    /**
     * Checks for notification permission on Android 13+ (API level 33).
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Notification permission not granted")
            }
        }
    }

    /**
     * Starts the service in the foreground with a notification.
     */
    private fun startForegroundService(config: ForegroundCallServiceConfig) {
        Log.d(TAG, "Starting foreground service")
        notificationBuilder.setTitle(config.androidNotificationName!!)
        notificationBuilder.setContent(config.androidNotificationDescription!!)
        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ForegroundCallNotificationBuilder.FOREGROUND_CALL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(
                ForegroundCallNotificationBuilder.FOREGROUND_CALL_NOTIFICATION_ID, notification
            )
        }
    }

    /**
     * Ensures that the notification is visible. If not, it restarts the foreground service.
     */
    private fun ensureNotification(config: ForegroundCallServiceConfig) {
        if (!isNotificationVisible()) {
            Log.d(TAG, "Notification not visible, restarting foreground service")
            startForegroundService(config)
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
        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)

        if (config.autoRestartOnTerminate) {
            ForegroundCallWorker.Companion.enqueue(this)
        }

        getLock(applicationContext)?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        flutterEngineHelper.detachAndDestroyEngine()

        isRunning.set(false)
        unregister()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ForegroundCallServiceEnums.INIT.action
        val data = intent?.extras

        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)

        ensureNotification(config)

        when (action) {
            ForegroundCallServiceEnums.INIT.action -> runService(config)
            ForegroundCallServiceEnums.START.action -> wakeUp(config, data)
            ForegroundCallServiceEnums.STOP.action -> tearDown()
            ForegroundCallServiceEnums.CHANGE_LIFECYCLE.action -> onChangedLifecycleHandler(data)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved")
        if (isRunning.get()) {
            ForegroundCallWorker.Companion.enqueue(applicationContext, 1000)
        }
    }

    /**
     * Wakes up the service and sends a broadcast to synchronize call status.
     */
    private fun wakeUp(config: ForegroundCallServiceConfig, data: Bundle?) {
        runService(config)
        wakeUpBackgroundHandler(data)
//        ForegroundCallServiceReceiver.wakeUp(applicationContext, data?.getString(PARAM_JSON_DATA))
    }

    /**
     * Tears down the service gracefully.
     */
    private fun tearDown() {
        ForegroundCallWorker.Companion.remove(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Runs the service and starts the Flutter background isolate.
     */
    @SuppressLint("WakelockTimeout")
    private fun runService(config: ForegroundCallServiceConfig) {
        if (config.autoRestartOnTerminate) {
            ForegroundCallWorker.Companion.enqueue(applicationContext)
        }

        Log.v(TAG, "Running service logic")
        getLock(applicationContext)?.acquire(10 * 60 * 1000L /*10 minutes*/)

        flutterEngineHelper.startOrAttachEngine()

        isRunning.set(true)

        connectionService = CallkeepApiProvider.getBackgroundCallkeepApi(baseContext, _isolateCalkeepFlutterApi!!)

        incomingCallReceiver = IncomingCallNotificationReceiver(
            baseContext,
            endCall = { callMetaData -> connectionService.hungUp(callMetaData) {} },
            answerCall = { callMetaData -> connectionService.answer(callMetaData) },
        )

        register()
    }


    private fun wakeUpBackgroundHandler(
        extras: Bundle?
    ) {

        Log.d(TAG, "onWakeUpBackgroundHandler")

        val lifecycle = ActivityHolder.getActivityState()
        val lockScreen = Platform.isLockScreen(baseContext)
        val pLifecycle = lifecycle.toPCallkeepLifecycleType()

        val activityReady = StorageDelegate.getActivityReady(baseContext)
        val wakeUpHandler = StorageDelegate.getOnStartHandler(baseContext)

        val jsonData = extras?.getString(PARAM_JSON_DATA) ?: "{}"

        _isolatePushNotificationFlutterApi?.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                pLifecycle,
                lockScreen,
                activityReady,
                PhoneConnectionService.connectionManager.isExistsActiveConnection(),
                jsonData
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


    @Suppress("DEPRECATION", "KotlinConstantConditions")
    private fun onChangedLifecycleHandler(bundle: Bundle?) {
        val activityReady = StorageDelegate.getActivityReady(baseContext)
        val lockScreen = Platform.isLockScreen(baseContext)
        val event = bundle?.getSerializable(PARAM_CHANGE_LIFECYCLE_EVENT) as Lifecycle.Event?

        val lifecycle = (event ?: Lifecycle.Event.ON_ANY).toPCallkeepLifecycleType()
        val onChangedLifecycleHandler = StorageDelegate.getOnChangedLifecycleHandler(baseContext)

        _isolatePushNotificationFlutterApi?.onApplicationStatusChanged(
            onChangedLifecycleHandler, PCallkeepServiceStatus(
                lifecycle,
                lockScreen,
                activityReady,
                PhoneConnectionService.connectionManager.isExistsActiveConnection(), "{}",
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

    companion object {
        private const val TAG = "ForegroundCallService"
        private const val PARAM_JSON_DATA = "PARAM_JSON_DATA"
        private const val PARAM_CHANGE_LIFECYCLE_EVENT = "PARAM_CHANGE_LIFECYCLE_EVENT"


        @JvmStatic
        val isRunning = AtomicBoolean(false)

        /**
         * Communicates with the service by starting it with the specified action and metadata.
         */
        private fun communicate(context: Context, action: ForegroundCallServiceEnums, metadata: Bundle?) {
            val intent = Intent(context, ForegroundCallService::class.java).apply {
                this.action = action.action
                metadata?.let { putExtras(it) }
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot start service: ${e.message}", e)
            }
        }

        /**
         * Wakes up the service with an option to auto-restart.
         */
        fun start(context: Context, data: String? = null) {
            val bundleData = data?.let { Bundle().apply { putString(PARAM_JSON_DATA, it) } }
            communicate(context, ForegroundCallServiceEnums.START, bundleData)
        }

        /**
         * Tears down the service.
         */
        fun stop(context: Context) {
            communicate(context, ForegroundCallServiceEnums.STOP, null)
        }

        fun changeLifecycle(context: Context, event: Lifecycle.Event) {
            communicate(
                context,
                ForegroundCallServiceEnums.CHANGE_LIFECYCLE,
                Bundle().apply { putSerializable(PARAM_CHANGE_LIFECYCLE_EVENT, event) })
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

    override fun incomingCall(
        callId: String, handle: PHandle, displayName: String?, hasVideo: Boolean, callback: (Result<Unit>) -> Unit
    ) {

        val callPath = StorageDelegate.getIncomingPath(baseContext)
        val rootPath = StorageDelegate.getRootPath(baseContext)
        val ringtonePath = StorageDelegate.getRingtonePath(baseContext)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            paths = CallPaths(callPath, rootPath),
            ringtonePath = ringtonePath,
            createdTime = System.currentTimeMillis()
        )

        connectionService.incomingCall(callMetaData, callback)
    }

    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        val callMetaData = CallMetadata(callId = callId)
        connectionService.hungUp(callMetaData, callback)
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        connectionService.endAllCalls()
        callback.invoke(Result.success(Unit)) // TODO: Ensure proper cleanup of connections
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

enum class ForegroundCallServiceEnums {
    INIT, START, STOP, CHANGE_LIFECYCLE;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}

