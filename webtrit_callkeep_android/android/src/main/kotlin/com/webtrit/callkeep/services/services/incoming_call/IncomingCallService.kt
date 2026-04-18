package com.webtrit.callkeep.services.services.incoming_call

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.managers.NotificationChannelManager
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

        // Satisfy Android's 5-second startForeground() requirement immediately.
        // onStartCommand() may be delayed if the main thread is busy (e.g. platform-channel IPC
        // during Flutter cold-start) or if IC_RELEASE arrives before IC_INITIALIZE. Calling
        // startForeground() here — in onCreate() — prevents ForegroundServiceDidNotStartInTimeException
        // regardless of which action onStartCommand() processes first.
        //
        // IMPORTANT: use PLACEHOLDER_NOTIFICATION_ID here, NOT a call-derived notification ID.
        // The real incoming-call notification is posted by IncomingCallHandler with an ID derived
        // from the call ID (IncomingCallNotificationBuilder.notificationId(callId)). If the
        // placeholder used the same ID, the system would treat the real notification as an UPDATE
        // to the placeholder and suppress the fullScreenIntent — FSI fires only for newly-posted
        // notification IDs, not for updates to existing ones. A distinct placeholder ID ensures
        // that the real notification is always new from the system's perspective.
        // Android removes the placeholder automatically when the FGS transitions to the new ID.
        val placeholder =
            Notification
                .Builder(this, NotificationChannelManager.INCOMING_CALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                // No CATEGORY_CALL: Samsung/MIUI force all CATEGORY_CALL notifications to
                // show as heads-up even without title or text, producing a blank notification.
                // The placeholder is a technical FGS keepalive only — the real ringing
                // notification posted in handleLaunch() still uses CATEGORY_CALL.
                .setContentTitle(getString(R.string.incoming_call_title))
                .setContentText(getString(R.string.incoming_call_connecting))
                .setOngoing(true)
                .build()
        startForegroundServiceCompat(
            this,
            PLACEHOLDER_NOTIFICATION_ID,
            placeholder,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )

        Log.d(TAG, "IncomingCallService created")

        registerReceiverCompat(
            releaseReceiver,
            IntentFilter().apply {
                addAction(IncomingCallRelease.IC_RELEASE_WITH_DECLINE.name)
                addAction(IncomingCallRelease.IC_RELEASE_WITH_ANSWER.name)
            },
            exported = false,
        )

        CallkeepCore.instance.addConnectionEventListener(this)

        isolateHandler =
            FlutterIsolateHandler(this@IncomingCallService, this@IncomingCallService) {
                Log.d(TAG, "onStart: invoking syncPushIsolate, flutterApi=${callLifecycleHandler.flutterApi != null}, callData=${callLifecycleHandler.currentCallData?.callId}")
                callLifecycleHandler.flutterApi?.syncPushIsolate(
                    callLifecycleHandler.currentCallData,
                    onSuccess = { Log.d(TAG, "syncPushIsolate: success") },
                    onFailure = { e -> Log.e(TAG, "syncPushIsolate: failed: $e") },
                ) ?: Log.e(TAG, "syncPushIsolate: flutterApi is null, isolate will not receive call data")
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
        // Explicitly cancel the placeholder notification (ID=3) posted in onCreate().
        // If the FGS transitioned to a call-derived ID before this point, the placeholder
        // may not be auto-cancelled by Android on all OEM builds, leaving a blank
        // "Webtrit • now" notification that the user cannot dismiss (setOngoing=true).
        NotificationManagerCompat.from(this).cancel(PLACEHOLDER_NOTIFICATION_ID)
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
        if (!isFullScreenIntentAvailable()) {
            acquireScreenWakeLockIfNeeded()
        }
        // Remove the placeholder before posting the real notification.
        // NotificationManager.cancel() cannot remove an FGS-bound notification — it is
        // silently ignored when Android still considers the notification foreground-attached.
        // STOP_FOREGROUND_REMOVE removes the binding and the notification atomically,
        // guaranteeing the placeholder is gone before startForeground() in handle() posts
        // the real ringing notification. Both calls are synchronous on the main thread so
        // the service is never truly backgrounded between them.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.canUseFullScreenIntent()
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

        // Stable notification ID for the FGS placeholder posted in onCreate(). Must not
        // collide with IDs produced by IncomingCallNotificationBuilder.notificationId(callId)
        // (which are String.hashCode() values). Using a fixed sentinel keeps it simple; the
        // placeholder lives only until IncomingCallHandler replaces it with the real call
        // notification, so a hash collision (probability ~1 in 4 billion) would cause no
        // visible problem — the placeholder is removed either way.
        private const val PLACEHOLDER_NOTIFICATION_ID = 3

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
            context.sendInternalBroadcast(type.name)
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
