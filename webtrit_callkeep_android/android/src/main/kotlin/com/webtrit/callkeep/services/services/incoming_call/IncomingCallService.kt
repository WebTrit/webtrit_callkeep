package com.webtrit.callkeep.services.services.incoming_call

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.Keep
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PendingBroadcastQueue
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.models.toPCallkeepIncomingCallData
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
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

    // Set to true only after IC_INITIALIZE is handled. Guards against a duplicate IC_INITIALIZE
    // arriving while the service is still processing a call (e.g. a second push while the first
    // call is still in the teardown window).
    private var isInitialized = false

    // Set to true once syncPushIsolate has been dispatched. Prevents double-sync when both
    // the onStart() path (warm engine) and the establishFlutterCommunication() path (cold-start
    // deferred) would otherwise both deliver call data to the Dart isolate.
    private var callDataSynced = false

    // Receives IC_RELEASE_WITH_ANSWER / IC_RELEASE_WITH_DECLINE from release().
    // Registered in onCreate() and unregistered in onDestroy() so it only lives while the
    // service is alive. If the service is not running the broadcast goes nowhere — no zombie
    // restart, no placeholder notification appearing after the call ends.
    private val releaseReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name -> handleRelease(answered = false)
                    IncomingCallRelease.IC_RELEASE_WITH_ANSWER.name -> handleRelease(answered = true)
                }
            }
        }

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

        Log.d(TAG, "IncomingCallService created")

        registerReceiverCompat(
            releaseReceiver,
            IntentFilter().apply {
                addAction(IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name)
                addAction(IncomingCallRelease.IC_RELEASE_WITH_ANSWER.name)
            },
            exported = false,
            permission = RELEASE_BROADCAST_PERMISSION,
        )

        CallkeepCore.instance.addConnectionEventListener(this)

        isolateHandler =
            FlutterIsolateHandler(this@IncomingCallService, this@IncomingCallService) {
                Log.d(TAG, "onStart: invoking syncPushIsolate, flutterApi=${callLifecycleHandler.flutterApi != null}, callData=${callLifecycleHandler.currentCallData?.callId}")
                callLifecycleHandler.flutterApi?.syncPushIsolate(
                    callLifecycleHandler.currentCallData,
                    onSuccess = {
                        callDataSynced = true
                        Log.d(TAG, "syncPushIsolate: success")
                    },
                    onFailure = { e -> Log.e(TAG, "syncPushIsolate: failed: $e") },
                ) ?: Log.w(TAG, "syncPushIsolate: flutterApi is null — will retry from establishFlutterCommunication")
            }

        incomingCallHandler =
            IncomingCallHandler(
                service = this,
                notificationBuilder = incomingCallNotificationBuilder,
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

        return when (action) {
            PushNotificationServiceEnums.IC_INITIALIZE.name -> handleLaunch(metadata!!)

            // IC_RELEASE_WITH_ANSWER / IC_RELEASE_WITH_DECLINE are now delivered via
            // releaseReceiver (BroadcastReceiver registered in onCreate). They no longer
            // arrive through onStartCommand — release() uses sendInternalBroadcast() instead
            // of startService(), so the service is never restarted after it has stopped.

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
        unregisterReceiver(releaseReceiver)
        CallkeepCore.instance.removeConnectionEventListener(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        // Explicitly cancel the call-derived notification (ID ≥ 1000).
        // stopForeground(REMOVE) does not reliably remove the FGS notification on some
        // Samsung builds (Android 11), leaving buildSilent() or the ringing notification
        // lingering in the shade. cancelCurrentNotification() handles this explicitly.
        if (::incomingCallHandler.isInitialized) {
            incomingCallHandler.cancelCurrentNotification()
        }
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
        // On a cold-start Flutter engine, executeDartCallback() returns before Dart has run.
        // onStart() fires immediately and finds flutterApi == null, so syncPushIsolate is skipped.
        // This is the first point where Dart has registered its APIs and can accept the call data.
        if (!callDataSynced) {
            val data = callLifecycleHandler.currentCallData
            if (data != null) {
                callDataSynced = true
                Log.w(TAG, "establishFlutterCommunication: deferred sync for callId=${data.callId}")
                callLifecycleHandler.flutterApi?.syncPushIsolate(
                    data,
                    onSuccess = { Log.d(TAG, "syncPushIsolate (deferred): success") },
                    onFailure = { e -> Log.e(TAG, "syncPushIsolate (deferred): failed: $e") },
                )
            }
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
        // The service handles one call at a time. If IC_INITIALIZE arrives while we are
        // still in the teardown window of a previous call (stopTimeoutRunnable pending),
        // accepting it would cancel the stop timer, overwrite currentCallData, and start
        // a second Flutter isolate on the same engine — causing FlutterEngine conflicts and
        // callRejectedBySystem from Telecom. Reject the duplicate; it will be delivered to
        // a fresh service instance once the current one stops.
        if (isInitialized) {
            Log.w(TAG, "handleLaunch: already initialized for ${callLifecycleHandler.currentCallData?.callId}, ignoring IC_INITIALIZE for ${metadata.callId}")
            return START_NOT_STICKY
        }
        isInitialized = true

        // Check for a pending release posted by ForegroundService.reportEndCall() before
        // this service started. startForegroundService(IC_INITIALIZE) may be queued in the
        // OS before the caller hangs up. By the time the OS delivers it, reportEndCall() has
        // already run and posted to PendingBroadcastQueue. The IC_RELEASE_WITH_DECLINE
        // broadcast from :callkeep_core was lost (releaseReceiver not yet registered), so
        // this in-process entry is the only remaining signal that the call is over.
        if (PendingBroadcastQueue.consume(PendingBroadcastQueue.incomingReleaseKey(metadata.callId))) {
            Log.w(TAG, "handleLaunch: pending release found for callId=${metadata.callId} — releasing immediately without showing UI")
            // Block any deferred syncPushIsolate from establishFlutterCommunication(). The call
            // is already in teardown; delivering it to Dart as a new incoming call would create
            // a zombie ActiveCall on the Dart side that was never set up natively.
            callDataSynced = true
            // startForeground() must be called within 5s of startForegroundService() on Android 12+.
            // handle() satisfies this by posting a notification and calling startForeground().
            // releaseIncomingCallNotification() immediately transitions it to a silent release
            // notification, so the ringing UI is never visible to the user.
            incomingCallHandler.handle(metadata)
            callLifecycleHandler.currentCallData = metadata.toPCallkeepIncomingCallData()
            handleRelease(answered = false)
            return START_NOT_STICKY
        }
        timeoutHandler.removeCallbacks(stopTimeoutRunnable)
        timeoutHandler.removeCallbacks(independentTimeoutRunnable)
        timeoutHandler.postDelayed(independentTimeoutRunnable, INDEPENDENT_SERVICE_TIMEOUT_MS)
        callLifecycleHandler.currentCallData = metadata.toPCallkeepIncomingCallData()
        // Acquire the screen WakeLock only when USE_FULL_SCREEN_INTENT is unavailable
        // (e.g. MIUI/HyperOS where the permission is denied by default).
        //
        // When FSI is granted, acquiring a WakeLock here wakes the device from Doze
        // *before* the FSI notification is posted. On an already-awake device, SystemUI
        // no longer fires FSI as part of a Doze-exit sequence — VoipCallMonitor
        // (Android 14+) then intercepts the notification and silently suppresses FSI
        // because self-managed connections are not tracked in its call registry.
        //
        // When FSI is unavailable the WakeLock is the only mechanism to turn the screen
        // on; the notification provides the call UI instead of a full-screen Activity.
        val fsiAvailable = isFullScreenIntentAvailable()
        Log.d(TAG, "handleLaunch: isFullScreenIntentAvailable=$fsiAvailable → ${if (fsiAvailable) "relying on FSI" else "acquiring WakeLock fallback"}")
        if (!fsiAvailable) {
            acquireScreenWakeLockIfNeeded()
        }
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
     * Returns true if the system will fire a full-screen intent for this app's notifications,
     * meaning the WakeLock is not needed to wake the screen.
     *
     * On Android 13 and below there is no VoipCallMonitor and no USE_FULL_SCREEN_INTENT
     * permission gate, but acquiring the WakeLock on those versions is harmless and keeps
     * the pre-existing behavior. Returning false here causes handleLaunch() to always acquire
     * the WakeLock on API < 34, which is intentional.
     *
     * On Android 14+ (API 34) the permission can be denied by OEM ROMs (MIUI/HyperOS).
     * When denied, canUseFullScreenIntent() returns false and we fall back to the WakeLock.
     */
    private fun isFullScreenIntentAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "isFullScreenIntentAvailable: SDK ${Build.VERSION.SDK_INT} < 34, returning false (WakeLock path)")
            return false
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val available = nm.canUseFullScreenIntent()
        Log.d(TAG, "isFullScreenIntentAvailable: SDK ${Build.VERSION.SDK_INT}, canUseFullScreenIntent=$available")
        return available
    }

    /**
     * Acquires a wake lock that turns on the screen when an incoming call arrives on
     * devices where USE_FULL_SCREEN_INTENT is unavailable (e.g. MIUI/HyperOS).
     *
     * SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP is required to physically turn the
     * screen on via PowerManager. On these devices the notification provides the call UI
     * instead of a full-screen Activity; the wake lock makes it visible.
     *
     * This must NOT be acquired before the FSI notification is posted on devices where
     * FSI is available. Waking the device from Doze first changes the timing so that
     * VoipCallMonitor (Android 14+) intercepts the FSI notification on an already-awake
     * device, preventing SystemUI from firing it as part of the Doze-exit sequence.
     *
     * The lock expires automatically after WAKELOCK_TIMEOUT_MS to prevent battery
     * drain if the release path is skipped.
     */
    @Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK is deprecated but is the correct flag
    // for waking the screen; the modern alternative (FLAG_TURN_SCREEN_ON) requires an Activity.
    private fun acquireScreenWakeLockIfNeeded() {
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
        private const val RELEASE_BROADCAST_PERMISSION = "com.webtrit.callkeep.INTERNAL_BROADCAST"

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
            // Deliver via broadcast instead of startService().
            // releaseReceiver is registered in onCreate() and unregistered in onDestroy(),
            // so it is alive only while the service is running. If the service has already
            // stopped the broadcast goes nowhere — no zombie restart, no placeholder
            // notification appearing after the call ends.
            //
            // sendInternalBroadcast() uses setPackage(packageName) + FLAG_RECEIVER_FOREGROUND,
            // so it crosses the :callkeep_core → main-process boundary safely and is
            // delivered only to this app.
            context.sendInternalBroadcast(type.name, permission = RELEASE_BROADCAST_PERMISSION)
            Log.d(TAG, "Release action $type initiated via broadcast.")
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
