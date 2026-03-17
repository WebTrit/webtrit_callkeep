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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepServiceStatus
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostBackgroundSignalingIsolateApi
import com.webtrit.callkeep.common.CallDataConst
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
import com.webtrit.callkeep.notifications.ForegroundCallNotificationBuilder.Companion.ACTION_RESTORE_NOTIFICATION
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleBroadcaster
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.broadcaster.SignalingStatusBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
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
    private var latestLifecycleActivityEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY

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
            synchronizeSignalingIsolate(latestLifecycleActivityEvent, latestSignalingStatus)

        }
    }

    private val lifecycleEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            latestLifecycleActivityEvent = Lifecycle.Event.fromBundle(intent?.extras) ?: return
            synchronizeSignalingIsolate(latestLifecycleActivityEvent, latestSignalingStatus)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)

        Log.d(TAG, "SignalingIsolateService onCreate")
        // Register the service to receive signaling status updates
        latestSignalingStatus = SignalingStatusBroadcaster.currentValue
        SignalingStatusBroadcaster.register(this, signalingStatusReceiver)

        // Register the service to receive lifecycle events
        latestLifecycleActivityEvent = ActivityLifecycleBroadcaster.currentValue ?: Lifecycle.Event.ON_DESTROY
        ActivityLifecycleBroadcaster.register(this, lifecycleEventReceiver)

        notificationBuilder = ForegroundCallNotificationBuilder()

        startForegroundService()

        val callbackDispatcher =
            StorageDelegate.SignalingService.getCallbackDispatcher(applicationContext)
        flutterEngineHelper = FlutterEngineHelper(applicationContext, callbackDispatcher, this)

        isRunning = true
    }

    override fun onDestroy() {
        Log.d(TAG, "SignalingIsolateService onDestroy")

        // Unregister the service from receiving signaling status updates
        SignalingStatusBroadcaster.unregister(this, signalingStatusReceiver)
        latestSignalingStatus = null

        // Unregister the service from receiving lifecycle events
        ActivityLifecycleBroadcaster.unregister(this, lifecycleEventReceiver)

        if (StorageDelegate.SignalingService.isSignalingServiceEnabled(context = applicationContext)) {
            SignalingServiceBootWorker.enqueue(this)
        }

        wakeLock?.let { lock ->
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
        notificationBuilder.setTitle(
            StorageDelegate.SignalingService.getNotificationTitle(
                applicationContext
            )
        )
        notificationBuilder.setContent(
            StorageDelegate.SignalingService.getNotificationDescription(
                applicationContext
            )
        )
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
        Log.d(TAG, "SignalingIsolateService onStartCommand: $intent")

        val action = intent?.action
        val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)

        when (action) {
            ACTION_RESTORE_NOTIFICATION -> {
                Log.d(TAG, "User removed notification, restoring")
                ensureNotification()
            }

            ForegroundCallServiceEnums.DECLINE.action -> {
                metadata?.let {
                    PhoneConnectionService.startHungUpCall(baseContext, it)
                    ensureNotification()
                } ?: Log.w(TAG, "Missing metadata for DECLINE action")
            }

            ForegroundCallServiceEnums.ANSWER.action -> {
                metadata?.let {
                    PhoneConnectionService.startAnswerCall(baseContext, it)
                    ensureNotification()
                } ?: Log.w(TAG, "Missing metadata for ANSWER action")
            }

            else -> {
                ensureNotification()
            }
        }

        getLock(applicationContext).acquire(10 * 60 * 1000L)

        flutterEngineHelper.startOrAttachEngine()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "SignalingIsolateService onTaskRemoved: $rootIntent")
        if (StorageDelegate.SignalingService.isSignalingServiceEnabled(context = applicationContext)) {
            SignalingServiceBootWorker.enqueue(applicationContext, 1000)
        }
    }

    @Suppress("DEPRECATION")
    private fun synchronizeSignalingIsolate(
        activityLifecycle: Lifecycle.Event, status: SignalingStatus?
    ) {
        val wakeUpHandler = StorageDelegate.SignalingService.getOnSyncHandler(baseContext)

        Log.d(TAG, "SignalingIsolateService synchronizeSignalingIsolate wakeUpHandler: $wakeUpHandler status: $status")
        _isolateSignalingFlutterApi?.onWakeUpBackgroundHandler(
            wakeUpHandler, PCallkeepServiceStatus(
                activityLifecycle.toPCallkeepLifecycleType(),
                mainSignalingStatus = status?.toPCallkeepSignalingStatus()
            ), null
        ) { response -> }
    }

    override fun incomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<Unit>) -> Unit
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

    /**
     * Ends a single active call identified by [callId].
     *
     * Sends a hang-up intent to [PhoneConnectionService] and then waits for a
     * [CallLifecycleEvent.HungUp] or [CallLifecycleEvent.DeclineCall] broadcast that carries
     * the same [callId] before resolving [callback] with success. This ensures Dart is not
     * notified before the Telecom framework has actually torn down the connection.
     *
     * If no confirmation broadcast arrives within [END_CALL_TIMEOUT_MS] milliseconds the
     * callback is resolved anyway and a warning is logged, so the Dart side is never left
     * hanging indefinitely.
     *
     * [AtomicBoolean] guarantees the callback is invoked exactly once even when the broadcast
     * and the timeout race each other.
     *
     * @param callId  Identifier of the call to terminate.
     * @param callback Pigeon-generated callback; receives [Result.success] on completion.
     */
    override fun endCall(callId: String, callback: (Result<Unit>) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val resolved = AtomicBoolean(false)
        lateinit var receiver: BroadcastReceiver

        fun finish() {
            if (!resolved.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, receiver) } catch (_: IllegalArgumentException) {}
            callback(Result.success(Unit))
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.extras?.getString(CallDataConst.CALL_ID) ?: return
                if (id == callId) finish()
            }
        }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.HungUp, CallLifecycleEvent.DeclineCall), baseContext, receiver,
            exported = false,
        )

        handler.postDelayed({
            Log.w(TAG, "endCall timeout waiting for confirmation, callId: $callId")
            finish()
        }, END_CALL_TIMEOUT_MS)

        PhoneConnectionService.startHungUpCall(baseContext, CallMetadata(callId = callId))
    }

    /**
     * Tears down all active calls managed by [PhoneConnectionService].
     *
     * Snapshots the currently tracked connections before issuing the teardown intent:
     *
     * - **No active connections** — teardown is sent and [callback] is resolved immediately,
     *   since there are no broadcasts to wait for.
     * - **One or more active connections** — a [BroadcastReceiver] is registered for
     *   [CallLifecycleEvent.HungUp] / [CallLifecycleEvent.DeclineCall]. An [AtomicInteger]
     *   counts down each arriving broadcast; [callback] is resolved once the counter reaches
     *   zero, meaning every tracked connection has confirmed teardown.
     *
     * A [END_CALL_TIMEOUT_MS]-millisecond safety timeout resolves the callback and unregisters
     * the receiver if not all confirmations arrive in time, logging how many were still pending.
     *
     * [AtomicBoolean] guarantees the callback is invoked exactly once regardless of whether
     * the countdown or the timeout wins the race.
     *
     * @param callback Pigeon-generated callback; receives [Result.success] on completion.
     */
    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        val active = PhoneConnectionService.connectionManager.getConnections()

        if (active.isEmpty()) {
            PhoneConnectionService.tearDown(baseContext)
            callback(Result.success(Unit))
            return
        }

        val pendingIds = Collections.synchronizedSet(active.map { it.callId }.toMutableSet())
        val handler = Handler(Looper.getMainLooper())
        val resolved = AtomicBoolean(false)
        lateinit var receiver: BroadcastReceiver

        fun finish() {
            if (!resolved.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, receiver) } catch (_: IllegalArgumentException) {}
            callback(Result.success(Unit))
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.extras?.getString(CallDataConst.CALL_ID) ?: return
                if (pendingIds.remove(id) && pendingIds.isEmpty()) finish()
            }
        }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.HungUp, CallLifecycleEvent.DeclineCall), baseContext, receiver,
            exported = false,
        )

        handler.postDelayed({
            Log.w(TAG, "endAllCalls timeout waiting for ${pendingIds.size} remaining confirmation(s)")
            finish()
        }, END_CALL_TIMEOUT_MS)

        PhoneConnectionService.tearDown(baseContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SignalingIsolateService"
        private const val WAKE_LOCK_TAG = "com.webtrit.callkeep:SignalingIsolateService.Lock"
        private const val END_CALL_TIMEOUT_MS = 5_000L

        var isRunning = false

        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        /**
         * Returns the cached partial wake lock, creating it on first call.
         * Using a single instance ensures acquire/release operate on the same object.
         */
        @Synchronized
        fun getLock(context: Context): PowerManager.WakeLock {
            return wakeLock ?: run {
                val mgr = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }.also { wakeLock = it }
            }
        }

        /** Resets the cached wake lock. Intended for use in tests only. */
        @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
        internal fun resetWakeLock() {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }

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
            Log.d(TAG, "Stopping SignalingIsolateService")

            SignalingServiceBootWorker.remove(context)

            context.stopService(Intent(context, SignalingIsolateService::class.java))
        }

    }
}

enum class ForegroundCallServiceEnums {
    ANSWER, DECLINE;

    val action: String
        get() = ContextHolder.appUniqueKey + name + "_foreground_call_service"
}
