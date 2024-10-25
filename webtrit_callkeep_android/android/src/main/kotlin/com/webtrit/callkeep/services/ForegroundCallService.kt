package com.webtrit.callkeep.services

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
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Constants
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.models.ForegroundCallServiceConfig
import com.webtrit.callkeep.common.models.ForegroundCallServiceHandles
import com.webtrit.callkeep.common.notifications.ForegroundCallNotificationBuilder
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback
import io.flutter.view.FlutterCallbackInformation
import java.util.concurrent.atomic.AtomicBoolean

class ForegroundCallService : Service() {
    private lateinit var notificationBuilder: ForegroundCallNotificationBuilder

    private var isRunning = AtomicBoolean(false)
    private var isEngineAttached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = ForegroundCallNotificationBuilder(applicationContext)

        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)

        checkNotificationPermission()
        startForegroundService(config)
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
        val notification = notificationBuilder.build(
            config.androidNotificationName!!, config.androidNotificationDescription!!
        )

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
            ForegroundCallWorker.enqueue(this)
        }

        getLock(applicationContext)?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)

        isRunning.set(false)
        isEngineAttached = false

        // Detach FlutterEngine without destroying it
        backgroundEngine?.serviceControlSurface?.detachFromService()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ForegroundCallServiceEnums.INIT.action

        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)
        val handles = StorageDelegate.getForegroundCallServiceHandles(applicationContext)

        ensureNotification(config)

        when (action) {
            ForegroundCallServiceEnums.INIT.action -> runService(config, handles)
            ForegroundCallServiceEnums.START.action -> wakeUp(intent, config, handles)
            ForegroundCallServiceEnums.STOP.action -> tearDown()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved")
        if (isRunning.get()) {
            ForegroundCallWorker.enqueue(applicationContext, 1000)
        }
    }

    /**
     * Wakes up the service and sends a broadcast to synchronize call status.
     */
    private fun wakeUp(intent: Intent?, config: ForegroundCallServiceConfig, handles: ForegroundCallServiceHandles) {
        val data = intent?.getStringExtra(PARAM_START_DATA) ?: Constants.EMPTY_JSON_MAP

        runService(config, handles)
        ForegroundCallServiceReceiver.wakeUp(applicationContext, data)
    }

    /**
     * Tears down the service gracefully.
     */
    private fun tearDown() {
        ForegroundCallWorker.remove(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Runs the service and starts the Flutter background isolate.
     */
    @SuppressLint("WakelockTimeout")
    private fun runService(config: ForegroundCallServiceConfig, handles: ForegroundCallServiceHandles) {
        if (config.autoRestartOnTerminate) {
            ForegroundCallWorker.enqueue(applicationContext)
        }

        Log.v(TAG, "Running service logic")
        getLock(applicationContext)?.acquire(10 * 60 * 1000L /*10 minutes*/)

        if (backgroundEngine == null) {
            Log.v(TAG, "Starting new background isolate")
            startBackgroundIsolate(this, handles.callbackDispatcher)
            isEngineAttached = true
        } else {
            if (!isEngineAttached) {
                Log.v(TAG, "Reattaching to existing FlutterEngine")
                backgroundEngine?.serviceControlSurface?.attachToService(this, null, true)
                isEngineAttached = true
            } else {
                Log.v(TAG, "FlutterEngine is already attached to service")
            }
        }
        isRunning.set(true)
    }

    /**
     * Starts the Flutter background isolate using the provided callback handle.
     */
    private fun startBackgroundIsolate(context: Context, callbackHandle: Long) {
        try {
            val flutterLoader = FlutterInjector.instance().flutterLoader()
            if (!flutterLoader.initialized()) {
                flutterLoader.startInitialization(context.applicationContext)
            }
            flutterLoader.ensureInitializationComplete(context.applicationContext, null)

            backgroundEngine = FlutterEngine(context.applicationContext)

            val callbackInformation = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
            if (callbackInformation != null) {
                val dartCallback = DartCallback(
                    context.assets, flutterLoader.findAppBundlePath(), callbackInformation
                )
                backgroundEngine?.dartExecutor?.executeDartCallback(dartCallback)
                backgroundEngine?.serviceControlSurface?.attachToService(this, null, true)
                isEngineAttached = true
            } else {
                Log.e(TAG, "Invalid callback handle: $callbackHandle")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background isolate", e)
        }
    }

    companion object {
        private var backgroundEngine: FlutterEngine? = null
        private const val TAG = "ForegroundCallService"

        private const val PARAM_START_DATA = "PARAM_WAKE_UP_DATA"

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
        fun start(context: Context, data: String) {
            communicate(context, ForegroundCallServiceEnums.START, Bundle().apply {
                putString(PARAM_START_DATA, data)
            })
        }

        /**
         * Tears down the service.
         */
        fun stop(context: Context) {
            communicate(context, ForegroundCallServiceEnums.STOP, null)
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
    INIT, START, STOP;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}

