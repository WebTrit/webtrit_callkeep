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
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.FlutterEngineHelper
import com.webtrit.callkeep.models.ForegroundCallServiceConfig
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder
import java.util.concurrent.atomic.AtomicBoolean

class ForegroundCallService : Service() {
    private lateinit var notificationBuilder: ForegroundCallNotificationBuilder
    private lateinit var flutterEngineHelper: FlutterEngineHelper

    override fun onBind(intent: Intent?): IBinder? = null

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

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ForegroundCallServiceEnums.INIT.action
        val data = intent?.extras

        val config = StorageDelegate.getForegroundCallServiceConfiguration(applicationContext)

        ensureNotification(config)

        when (action) {
            ForegroundCallServiceEnums.INIT.action -> runService(config, data)
            ForegroundCallServiceEnums.START.action -> wakeUp(config, data)
            ForegroundCallServiceEnums.STOP.action -> tearDown()
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
        runService(config, data)
        ForegroundCallServiceReceiver.wakeUp(applicationContext, data?.getString(PARAM_JSON_DATA))
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
    private fun runService(config: ForegroundCallServiceConfig, data: Bundle?) {
        if (config.autoRestartOnTerminate) {
            ForegroundCallWorker.Companion.enqueue(applicationContext)
        }

        Log.v(TAG, "Running service logic")
        getLock(applicationContext)?.acquire(10 * 60 * 1000L /*10 minutes*/)

        flutterEngineHelper.startOrAttachEngine()

        isRunning.set(true)
    }

    companion object {
        private const val TAG = "ForegroundCallService"
        private const val PARAM_JSON_DATA = "PARAM_JSON_DATA"

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

