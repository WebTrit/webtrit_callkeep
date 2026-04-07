package com.webtrit.callkeep.services.services.incoming_call

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.models.toPCallkeepIncomingCallData
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.common.DefaultIsolateLaunchPolicy
import com.webtrit.callkeep.services.core.CallkeepCore
import com.webtrit.callkeep.services.core.ConnectionEventListener
import com.webtrit.callkeep.services.services.incoming_call.handlers.CallLifecycleHandler
import com.webtrit.callkeep.services.services.incoming_call.handlers.FlutterIsolateHandler
import com.webtrit.callkeep.services.services.incoming_call.handlers.IncomingCallHandler

@Keep
class IncomingCallService :
    Service(),
    ConnectionEventListener {
    private val incomingCallNotificationBuilder by lazy { IncomingCallNotificationBuilder() }

    // Held while an incoming call is ringing. Wakes the screen on devices where
    // full-screen intent is restricted (e.g. MIUI/HyperOS with USE_FULL_SCREEN_INTENT
    // denied). Released in onDestroy() or when the call is answered/declined.
    private var screenWakeLock: PowerManager.WakeLock? = null

    // Set to true only after IC_INITIALIZE is handled. Guards against spurious IC_RELEASE_WITH_DECLINE
    // intents that restart the service after it has already been stopped (e.g. when releaseCall()
    // calls stopSelf() and Telecom later triggers PhoneConnection.onDisconnect → startService).
    private var isInitialized = false

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val stopTimeoutRunnable =
        Runnable {
            Log.w(TAG, "Service stop timeout ($SERVICE_TIMEOUT_MS ms) reached. Stopping forcefully.")
            stopSelf()
        }

    private val independentTimeoutRunnable =
        Runnable {
            Log.w(TAG, "Independent service timeout ($INDEPENDENT_SERVICE_TIMEOUT_MS ms) reached. Stopping forcefully.")
            stopSelf()
        }

    private lateinit var incomingCallHandler: IncomingCallHandler
    private lateinit var isolateHandler: FlutterIsolateHandler
    private lateinit var callLifecycleHandler: CallLifecycleHandler

    fun getCallLifecycleHandler(): CallLifecycleHandler = callLifecycleHandler

    override fun onConnectionEvent(
        event: ConnectionEvent,
        data: Bundle?,
    ) {
        // Only handle AnswerCall. DeclineCall and HungUp are handled via IC_RELEASE_WITH_DECLINE
        // intent (triggered from PhoneConnection.onDisconnect -> cancelIncomingNotification).
        // Handling them here as well would cause a double performEndCall: once from handleRelease
        // and once from this listener, racing to tear down the WebSocket before the SIP BYE is
        // sent. The IC_RELEASE_WITH_DECLINE path is the single authoritative source for decline
        // teardown.
        if (event == CallLifecycleEvent.AnswerCall) {
            val metadata = data?.let(CallMetadata::fromBundleOrNull) ?: return
            performAnswerCall(metadata)
        }
    }

    override fun onCreate() {
        super.onCreate()
        setRunning(true)
        ContextHolder.init(applicationContext)

        // Satisfy Android's 5-second startForeground() requirement immediately.
        // onStartCommand() may be delayed if the main thread is busy (e.g. platform-channel IPC
        // during Flutter cold-start) or if IC_RELEASE arrives before IC_INITIALIZE. Calling
        // startForeground() here — in onCreate() — prevents ForegroundServiceDidNotStartInTimeException
        // regardless of which action onStartCommand() processes first.
        // When IC_INITIALIZE later arrives, incomingCallHandler.handle() calls startForeground()
        // again with the full incoming-call notification, which simply replaces this placeholder.
        val placeholder =
            Notification
                .Builder(this, NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .build()
        startForegroundServiceCompat(
            this,
            IncomingCallNotificationBuilder.NOTIFICATION_ID,
            placeholder,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )

        Log.d(TAG, "IncomingCallService created")

        CallkeepCore.instance.addConnectionEventListener(this)

        isolateHandler =
            FlutterIsolateHandler(this@IncomingCallService, this@IncomingCallService) {
                callLifecycleHandler.flutterApi?.syncPushIsolate(callLifecycleHandler.currentCallData, onSuccess = {}, onFailure = {})
            }

        incomingCallHandler =
            IncomingCallHandler(
                service = this,
                notificationBuilder = incomingCallNotificationBuilder,
                isolateLaunchPolicy = DefaultIsolateLaunchPolicy(this),
                isolateInitializer = isolateHandler,
            )

        callLifecycleHandler =
            CallLifecycleHandler(
                connectionController = DefaultCallConnectionController(),
                stopService = { stopSelf() },
                isolateHandler = isolateHandler,
            )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)
        val action = intent?.action

        Log.d(TAG, "onStartCommand: $action, $metadata")

        if (startId == 0 && action != PushNotificationServiceEnums.IC_INITIALIZE.name) {
            Log.w(TAG, "Service was not properly started (startId=0), stopping to avoid crash")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isInitialized && action == IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name) {
            Log.w(TAG, "onStartCommand: IC_RELEASE_WITH_DECLINE received before IC_INITIALIZE — service was restarted after stop, ignoring")
            stopSelf()
            return START_NOT_STICKY
        }

        return when (action) {
            // Listen foreground service actions
            PushNotificationServiceEnums.IC_INITIALIZE.name -> handleLaunch(metadata!!)

            IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name -> handleRelease(answered = false)

            IncomingCallRelease.IC_RELEASE_WITH_ANSWER.name -> handleRelease(answered = true)

            // Listen push notification actions (Only notify connection service)
            NotificationAction.Answer.action -> reportAnswerToConnectionService(metadata!!)

            NotificationAction.Decline.action -> reportHungUpToConnectionService(metadata!!)

            else -> handleUnknownAction(action)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")

        // Cancel both safety-net timeouts so they do not fire after a graceful stop.
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.removeCallbacks(independentTimeoutRunnable)

        setRunning(false)
        releaseScreenWakeLock()
        CallkeepCore.instance.removeConnectionEventListener(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        isolateHandler.cleanup()
        super.onDestroy()
    }

    fun establishFlutterCommunication(
        serviceApi: PDelegateBackgroundServiceFlutterApi,
        registerApi: PDelegateBackgroundRegisterFlutterApi,
    ) {
        callLifecycleHandler.apply {
            flutterApi =
                DefaultFlutterIsolateCommunicator(this@IncomingCallService, serviceApi, registerApi)
        }
    }

    private fun reportAnswerToConnectionService(metadata: CallMetadata): Int {
        callLifecycleHandler.reportAnswerToConnectionService(metadata)
        return START_NOT_STICKY
    }

    private fun reportHungUpToConnectionService(metadata: CallMetadata): Int {
        callLifecycleHandler.reportDeclineToConnectionService(metadata)
        return START_NOT_STICKY
    }

    private fun performAnswerCall(metadata: CallMetadata): Int {
        callLifecycleHandler.performAnswerCall(metadata)
        return START_STICKY
    }

    // Launches the service with the LAUNCH action and cancels the timeout
    private fun handleLaunch(metadata: CallMetadata): Int {
        isInitialized = true
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.postDelayed(independentTimeoutRunnable, INDEPENDENT_SERVICE_TIMEOUT_MS)
        callLifecycleHandler.currentCallData = metadata.toPCallkeepIncomingCallData()
        acquireScreenWakeLockIfNeeded()
        incomingCallHandler.handle(metadata)
        // START_NOT_STICKY: if the OS kills this service after the incoming call is set up,
        // do not restart it. A restart would deliver a null intent — the current onStartCommand
        // handler has no fallback for that path and Android would kill the process with
        // ForegroundServiceDidNotStartInTimeException (startForeground never called within 5s).
        // The call context is lost either way, so restarting adds no value.
        return START_NOT_STICKY
    }

    // Handles the RELEASE action and cancels the timeout
    private fun handleRelease(answered: Boolean = false): Int {
        // The ringing phase is over — release the wake lock immediately so the screen
        // is not held on for the full WAKELOCK_TIMEOUT_MS during post-call teardown.
        // onDestroy() keeps the lock as a final safety net in case this path is skipped.
        releaseScreenWakeLock()
        incomingCallHandler.releaseIncomingCallNotification(answered)
        timeoutHandler.removeCallbacks(independentTimeoutRunnable)
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.postDelayed(stopTimeoutRunnable, SERVICE_TIMEOUT_MS)
        if (answered) {
            // The call was answered. The background isolate is no longer needed for signaling —
            // the main process takes over the active-call session. Release resources immediately.
            callLifecycleHandler.release()
        } else {
            // The call was declined or hung up before being answered.
            // The signaling layer (WebSocket) must send a SIP BYE/decline to the server
            // BEFORE the WebSocket is torn down.
            //
            // Calling release() here directly (the old behaviour) would close the WebSocket
            // immediately, racing with the SIP BYE that performEndCall needs to send.
            //
            // Fix: call performEndCall first; its onSuccess/onFailure callbacks call release(),
            // which fires releaseResources and closes the WebSocket only after BYE completes
            // (or fails). If flutterApi is null, performEndCall falls back to release() directly
            // so cleanup always runs. The stopTimeoutRunnable above is an additional safety net
            // in case the Flutter isolate never responds.
            val callId = callLifecycleHandler.currentCallData?.callId
            if (callId != null) {
                callLifecycleHandler.performEndCall(CallMetadata(callId = callId))
            } else {
                Log.w(TAG, "handleRelease: no currentCallData, falling back to release()")
                callLifecycleHandler.release()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleUnknownAction(action: String?): Int {
        Log.e(TAG, "Unknown or missing intent action: $action")
        return START_NOT_STICKY
    }

    /**
     * Acquires a wake lock that turns on the screen when an incoming call arrives.
     *
     * This is a fallback for devices (MIUI/HyperOS) where USE_FULL_SCREEN_INTENT is
     * denied by default and the full-screen intent cannot wake the display. When
     * full-screen intent is available and enabled, the system handles screen wake via
     * the full-screen intent itself, so no wake lock is needed.
     *
     * The lock expires automatically after WAKELOCK_TIMEOUT_MS to prevent battery
     * drain if the release path is skipped.
     */
    @Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK is deprecated but is the correct flag
    // for waking the screen; the modern alternative (FLAG_TURN_SCREEN_ON) requires an Activity.
    private fun acquireScreenWakeLockIfNeeded() {
        val fullScreenEnabled = StorageDelegate.IncomingCall.isFullScreen(this)
        val canUseFullScreenIntent = PermissionsHelper(this).canUseFullScreenIntent()
        if (fullScreenEnabled && canUseFullScreenIntent) {
            Log.d(TAG, "Screen wake lock skipped: full-screen intent is available")
            return
        }
        if (screenWakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenWakeLock =
            pm
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    WAKELOCK_TAG,
                ).also { it.acquire(WAKELOCK_TIMEOUT_MS) }
        Log.d(TAG, "Screen wake lock acquired")
    }

    private fun releaseScreenWakeLock() {
        screenWakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Screen wake lock released")
            }
        }
        screenWakeLock = null
    }

    companion object {
        private const val TAG = "IncomingCallService"

        private const val SERVICE_TIMEOUT_MS = 2_000L
        private const val INDEPENDENT_SERVICE_TIMEOUT_MS = 60_000L
        private const val WAKELOCK_TIMEOUT_MS = 30_000L
        private const val WAKELOCK_TAG = "com.webtrit.callkeep:IncomingCallWakeLock"

        @Volatile
        var isRunning = false
            private set

        private fun setRunning(running: Boolean) {
            isRunning = running
        }

        fun start(
            context: Context,
            metadata: CallMetadata,
        ) {
            Log.d(TAG, "Starting IncomingCallService with metadata: $metadata")
            context.startForegroundService(
                Intent(context, IncomingCallService::class.java).apply {
                    this.action = PushNotificationServiceEnums.IC_INITIALIZE.name
                    metadata.toBundle().let(::putExtras)
                },
            )
        }

        // Method is invoked when the connection is disconnected and the incoming call can be released.
        // Stopping the service immediately would destroy the isolate, which can be critical if the signaling layer
        // still needs to be notified about the disconnection.
        // Instead, we initiate communication with the Flutter side and delay stopping the service to ensure a graceful shutdown.
        // During this time, the notification is replaced with a special "release" notification
        // using IncomingCallNotificationBuilder.buildReleaseNotification to inform the user that the call is being finalized.
        fun release(
            context: Context,
            type: IncomingCallRelease,
        ) {
            // Do NOT guard on isRunning here. isRunning is a static field set only in the
            // main-process JVM. After the :callkeep_core process split, PhoneConnection
            // (which runs in :callkeep_core) calls this method; in that JVM isRunning is
            // always false, so the guard would silently drop every cancel request and leave
            // the incoming-call notification frozen after answer/decline.
            //
            // startService() is intentional here, NOT startForegroundService().
            //
            // IC_RELEASE is only meaningful when IncomingCallService is already running
            // as a foreground service (started by IC_INITIALIZE). While that service is
            // alive the process is not treated as background by Android, so plain
            // startService() is allowed and delivers the action to onStartCommand().
            //
            // If IncomingCallService is NOT running we must not start it via
            // startForegroundService(): the release code path never calls startForeground(),
            // so Android would kill the app after the 5-second deadline with
            // ForegroundServiceDidNotStartInTimeException.
            // startService() instead fails with a caught IllegalStateException (background-
            // start restriction) which is the correct no-op: the notification is already
            // gone because the service was never running.
            runCatching {
                context.startService(
                    Intent(context, IncomingCallService::class.java).apply { this.action = type.name },
                )
                Log.d(TAG, "Release action $type initiated.")
            }.onFailure { e ->
                Log.w(TAG, "Release action $type: startService failed: $e")
            }
        }
    }
}

enum class IncomingCallRelease {
    IC_RELEASE_WITH_ANSWER,
    IC_RELEASE_WITH_DECLINE,
}

enum class PushNotificationServiceEnums {
    IC_INITIALIZE,
}
