package com.webtrit.callkeep.services.services.connection

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.startForegroundServiceCompat
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.AudioDevice
import com.webtrit.callkeep.models.AudioDeviceType
import com.webtrit.callkeep.models.CallConnectionState
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.StandaloneIncomingCallNotificationBuilder
import com.webtrit.callkeep.services.broadcaster.CallCommandEvent
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.core.CallkeepCore
import java.util.concurrent.ConcurrentHashMap
import com.webtrit.callkeep.managers.AudioManager as CallkeepAudioManager

/**
 * Standalone call management service for devices that do not expose the
 * `android.software.telecom` system feature (e.g. some tablets, Android Go builds,
 * certain custom OEM configurations).
 *
 * On devices with Telecom support, [PhoneConnectionService] (a [android.telecom.ConnectionService])
 * handles call management through the Android Telecom framework. On devices without Telecom,
 * this service acts as an independent call manager — tracking call state, managing audio
 * routing directly via [AudioManager], and dispatching the same [ConnectionEvent] broadcasts
 * that [PhoneConnectionService] produces in the Telecom path. This means [ForegroundService]
 * and the Flutter layer do not need to be aware of which path is active.
 *
 * The service runs in the main process (no `android:process` in the manifest).
 * It is started as a foreground service on the first incoming call and stopped when all
 * calls have ended or a teardown command is received.
 */
@Keep
class StandaloneCallService : Service() {
    private val core get() = CallkeepCore.instance
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val ringtoneManager by lazy { CallkeepAudioManager(applicationContext) }

    // Tracks whether startForeground() has been called in this service instance.
    // startForeground() is deferred until an actual call is handled so that lifecycle-only
    // commands (SyncConnectionState, SyncAudioState, etc.) do not post a foreground
    // notification when there is no call in progress.
    private var isForeground = false

    // FOREGROUND_SERVICE_TYPE_MICROPHONE was introduced in API 31 (Android 12).
    // On API 29-30 the type is unknown to the framework and startForeground() throws
    // IllegalArgumentException when the requested type does not match the manifest.
    // Passing null selects the 2-arg startForeground() fallback in startForegroundServiceCompat(),
    // which skips type validation on older builds.
    private val foregroundServiceType: Int?
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                null
            }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        AssetCacheManager.init(applicationContext)
        Log.initFromContext(applicationContext)
        // Register notification channels here as well as in ForegroundService.setUp().
        // This service may start before setUp() is invoked from the Flutter layer
        // (e.g. when SyncConnectionState is dispatched during app startup). Without this
        // call, startForeground() would crash with
        // CannotPostForegroundServiceNotificationException because the channel does not
        // yet exist in the system.
        NotificationChannelManager.registerNotificationChannels(applicationContext)
        isRunning = true
        // Do NOT call promoteToForeground() here.
        //
        // This service is started via two different mechanisms:
        //   1. startForegroundService() — for IncomingCall / OutgoingCall (call setup).
        //   2. startService()           — for all other commands (CleanConnections,
        //                                 TearDownConnections, AnswerCall, etc.).
        //
        // Calling startForeground(FOREGROUND_SERVICE_TYPE_PHONE_CALL) from onCreate()
        // when the service was launched via startService() crashes the :callkeep_core
        // process on some OEM devices (e.g. Lenovo TB300FU, Android 13). The repeated
        // crash marks the process as "bad", causing all subsequent service starts to be
        // suppressed by ActivityManager.
        //
        // Instead, promoteToForeground() is called at the very top of onStartCommand()
        // for IncomingCall and OutgoingCall only. Since onStartCommand() executes on the
        // main thread immediately after onCreate() returns, the 5-second startForeground()
        // window imposed by startForegroundService() is still satisfied — the dominant
        // delay is process startup time, not the transition between the two callbacks.
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action?.let { StandaloneServiceAction.from(it) }

        // Satisfy the 5-second startForeground() window for call-setup actions, which are the only
        // ones started via startForegroundService(). This MUST be decided from the raw action (not
        // the parsed command) and run BEFORE the command-parse bail-out below: if the call-setup
        // intent arrives with empty/truncated Binder extras, command parsing fails and an early
        // return without startForeground() would crash the process with
        // ForegroundServiceDidNotStartInTimeException ~5s later. All other actions arrive via
        // startService() and must NOT call startForeground() here — doing so from a
        // non-foreground-service start crashes the process on some OEM devices.
        if (action?.isCallSetup == true) {
            promoteToForeground()
        }

        // Parse the intent into a typed command once. Lifecycle commands carry no metadata, so
        // CallMetadata parsing never runs for them — closing the same eager-parse crash fixed on
        // the Telecom path (empty Binder-delivered Bundle -> uncaught IllegalArgumentException).
        val command =
            intent?.let { StandaloneServiceCommand.from(it) } ?: run {
                // Distinguish an unrecognised action from a known action whose required
                // callId/metadata is missing, so the log points at the actual failure.
                if (action != null) {
                    Log.w(TAG, "onStartCommand: action '$action' missing required callId/metadata, ignoring")
                } else {
                    Log.w(TAG, "onStartCommand: unknown or missing action '${intent?.action}', ignoring")
                }
                // Release the service if nothing keeps it alive — including the placeholder
                // foreground we may have just promoted for a call-setup action whose extras did
                // not parse — so it does not linger as a zombie (foreground) service.
                stopIfIdle()
                return START_NOT_STICKY
            }

        try {
            when (command) {
                is StandaloneServiceCommand.TearDown -> handleTearDownConnections()
                is StandaloneServiceCommand.Clean -> handleCleanConnections()
                is StandaloneServiceCommand.SyncAudio -> handleSyncAudioState()
                is StandaloneServiceCommand.SyncConnection -> handleSyncConnectionState()
                is StandaloneServiceCommand.Reserve -> handleReserveAnswer(command.callId)
                is StandaloneServiceCommand.Call -> dispatchCall(command.action, command.metadata)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception with action: ${intent?.action}", e)
        }

        // If no calls are active or pending after processing, there is nothing to keep alive.
        // This handles the case where a lifecycle-only command (SyncConnectionState,
        // SyncAudioState, CleanConnections) starts the service when no call is in progress.
        stopIfIdle()

        return START_NOT_STICKY
    }

    /**
     * Stops the service when no call is active or pending. Reached both after normal command
     * processing and on the command-parse bail-out path, so a call-setup intent that promoted to
     * foreground but failed to parse does not leave a zombie (foreground) service running.
     */
    private fun stopIfIdle() {
        if (callMetadataMap.isEmpty() && pendingAnswers.isEmpty()) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
        isRunning = false
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    /**
     * Routes a [StandaloneServiceCommand.Call] to its handler. [metadata] is guaranteed non-null
     * by [StandaloneServiceCommand.from], so the per-action `metadata?.let` guards that previously
     * silently dropped call actions on a malformed bundle are no longer needed.
     */
    private fun dispatchCall(
        action: StandaloneServiceAction,
        metadata: CallMetadata,
    ) {
        when (action) {
            StandaloneServiceAction.IncomingCall -> handleIncomingCall(metadata)

            StandaloneServiceAction.OutgoingCall -> handleOutgoingCall(metadata)

            StandaloneServiceAction.EstablishCall -> handleEstablishCall(metadata)

            StandaloneServiceAction.AnswerCall -> handleAnswerCall(metadata)

            StandaloneServiceAction.DeclineCall -> handleDeclineCall(metadata)

            StandaloneServiceAction.HungUpCall -> handleHungUpCall(metadata)

            StandaloneServiceAction.UpdateCall -> handleUpdateCall(metadata)

            StandaloneServiceAction.SendDtmf -> handleSendDtmf(metadata)

            StandaloneServiceAction.Holding -> handleHolding(metadata)

            StandaloneServiceAction.Muting -> handleMuting(metadata)

            StandaloneServiceAction.Speaker -> handleSpeaker(metadata)

            StandaloneServiceAction.AudioDeviceSet -> handleAudioDeviceSet(metadata)

            // Lifecycle and ReserveAnswer actions are modelled as dedicated command types and never
            // wrapped in Call, so they cannot reach this branch.
            StandaloneServiceAction.TearDownConnections,
            StandaloneServiceAction.CleanConnections,
            StandaloneServiceAction.ReserveAnswer,
            StandaloneServiceAction.SyncAudioState,
            StandaloneServiceAction.SyncConnectionState,
            -> Log.w(TAG, "dispatchCall: unexpected non-call action $action, ignoring")
        }
    }

    /**
     * Promotes the service to a foreground service.
     *
     * Called from [onStartCommand] for [StandaloneServiceAction.IncomingCall] and
     * [StandaloneServiceAction.OutgoingCall] — the only actions that arrive via
     * [android.content.Context.startForegroundService] and therefore impose the 5-second
     * [android.app.Service.startForeground] requirement. Also called from
     * [handleIncomingCall] and [handleOutgoingCall] as a no-op guard once already promoted.
     *
     * Uses [ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE] on API 31+ because this service
     * is only active on devices that do NOT have [android.software.telecom], making the
     * phone-call foreground type inappropriate. Microphone type is semantically correct for
     * a service that manages audio call sessions.
     *
     * On API 29-30 the microphone type flag did not exist; passing it to [startForeground]
     * causes the framework to throw [IllegalArgumentException] because the flag is not
     * recognised in that API level's manifest type validation. The 2-arg [startForeground]
     * overload (no type) is used instead, which bypasses the type check entirely and is safe
     * on all API levels below 31.
     */
    private fun promoteToForeground() {
        if (isForeground) return
        val placeholder =
            Notification
                .Builder(this, NotificationChannelManager.FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .build()
        startForegroundServiceCompat(this, NOTIFICATION_ID, placeholder, foregroundServiceType)
        isForeground = true
    }

    private fun handleIncomingCall(metadata: CallMetadata) {
        Log.i(TAG, "handleIncomingCall: callId=${metadata.callId}")
        promoteToForeground()
        callMetadataMap[metadata.callId] = metadata
        ringingIncomingCallIds.add(metadata.callId)
        answeredCallIds.remove(metadata.callId)
        if (answeredCallIds.isNotEmpty()) {
            Log.d(TAG, "handleIncomingCall: active call detected — playing call-waiting tone for callId=${metadata.callId}")
            ringtoneManager.startCallWaitingTone()
        } else {
            ringtoneManager.startRingtone(metadata.ringtonePath)
        }

        // Replace the placeholder foreground notification (posted by promoteToForeground) with
        // a full incoming call notification — including Answer/Decline buttons and CallStyle on
        // API 31+.  The placeholder uses FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID (low importance)
        // only to satisfy the 5-second startForeground() ANR window; this call switches to
        // INCOMING_CALL_NOTIFICATION_CHANNEL_ID (high importance) so the system treats it as a
        // ringing call rather than a silent ongoing service notification.
        showIncomingCallNotification(metadata)

        // Notify the main process that the call has been registered. ForegroundService listens
        // for this broadcast to resolve its pendingIncomingCallbacks entry and promote the call
        // into the core shadow state, matching the PhoneConnectionService.onCreateIncomingConnection
        // path in the Telecom-enabled flow.
        core.notifyConnectionEvent(CallLifecycleEvent.DidPushIncomingCall, metadata.toBundle())

        // If an answer was reserved before this call was registered (ReserveAnswer arrived first),
        // consume the pending reservation and immediately trigger the answer flow.
        if (pendingAnswers.remove(metadata.callId)) {
            handleAnswerCall(metadata)
        }
    }

    private fun showIncomingCallNotification(metadata: CallMetadata) {
        val notification =
            StandaloneIncomingCallNotificationBuilder()
                .apply { setCallMetaData(metadata) }
                .build()
        startForegroundServiceCompat(this, NOTIFICATION_ID, notification, foregroundServiceType)
    }

    private fun handleOutgoingCall(metadata: CallMetadata) {
        Log.i(TAG, "handleOutgoingCall: callId=${metadata.callId}")
        promoteToForeground()
        callMetadataMap[metadata.callId] = metadata
        answeredCallIds.remove(metadata.callId)
        // Notify the main process that the outgoing call is in progress, mirroring the
        // OngoingCall broadcast that PhoneConnectionService fires after onCreateOutgoingConnection.
        // ForegroundService listens for this to promote the call to STATE_DIALING and call performStartCall.
        core.notifyConnectionEvent(CallLifecycleEvent.OngoingCall, metadata.toBundle())
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        Log.i(TAG, "handleEstablishCall: callId=${metadata.callId}")
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
        val full = (callMetadataMap[metadata.callId] ?: metadata).mergeWith(metadata)
        callMetadataMap[metadata.callId] = full.copy(acceptedTime = System.currentTimeMillis())
        answeredCallIds.add(metadata.callId)
        pendingAnswers.remove(metadata.callId)

        activateAudio()
        fireInitialAudioState(metadata.callId)
        promoteRemainingRingingToCallWaitingTone(metadata.callId)

        core.notifyConnectionEvent(CallLifecycleEvent.AnswerCall, callMetadataMap[metadata.callId]!!.toBundle())
        // No onStateChanged here (no telecom Connection) — emit the state explicitly so the shadow
        // mirrors it (replaces the removed markAnswered state-stamping).
        core.notifyConnectionEvent(
            CallLifecycleEvent.ConnectionStateChanged,
            callMetadataMap[metadata.callId]!!.copy(connectionState = CallConnectionState.ACTIVE).toBundle(),
        )
    }

    private fun handleAnswerCall(metadata: CallMetadata) {
        Log.i(TAG, "handleAnswerCall: callId=${metadata.callId}")
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
        val full = (callMetadataMap[metadata.callId] ?: metadata).mergeWith(metadata)
        callMetadataMap[metadata.callId] = full.copy(acceptedTime = System.currentTimeMillis())
        answeredCallIds.add(metadata.callId)
        pendingAnswers.remove(metadata.callId)

        activateAudio()
        fireInitialAudioState(metadata.callId)
        promoteRemainingRingingToCallWaitingTone(metadata.callId)

        core.notifyConnectionEvent(CallLifecycleEvent.AnswerCall, callMetadataMap[metadata.callId]!!.toBundle())
        // No onStateChanged here (no telecom Connection) — emit the state explicitly so the shadow
        // mirrors it (replaces the removed markAnswered state-stamping).
        core.notifyConnectionEvent(
            CallLifecycleEvent.ConnectionStateChanged,
            callMetadataMap[metadata.callId]!!.copy(connectionState = CallConnectionState.ACTIVE).toBundle(),
        )
    }

    /**
     * After [answeredCallId] is answered the loud ringtone is stopped, but a second incoming call
     * may still be ringing. Start the quiet call-waiting tone for it so it is not silently dropped,
     * matching the start-path branch in [handleIncomingCall] that plays the call-waiting tone when
     * a call is already active.
     */
    private fun promoteRemainingRingingToCallWaitingTone(answeredCallId: String) {
        if (hasOtherRingingCall(ringingIncomingCallIds, answeredCallIds, answeredCallId)) {
            Log.d(TAG, "promoteRemainingRingingToCallWaitingTone: another call still ringing")
            ringtoneManager.startCallWaitingTone()
        }
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        Log.i(TAG, "handleDeclineCall: callId=${metadata.callId}")
        stopRingtoneUnlessOtherCallRinging(metadata.callId)
        endCall(metadata)
        core.notifyConnectionEvent(CallLifecycleEvent.HungUp, metadata.toBundle())
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        Log.i(TAG, "handleHungUpCall: callId=${metadata.callId}")
        stopRingtoneUnlessOtherCallRinging(metadata.callId)
        endCall(metadata)
        core.notifyConnectionEvent(CallLifecycleEvent.HungUp, metadata.toBundle())
    }

    /**
     * Stop the shared ringtone / call-waiting tone only when no OTHER call is still ringing.
     *
     * The ringtone is a single shared instance (see [CallkeepAudioManager]); stopping it on the
     * disconnect of one call silences every other call too. When a first incoming call times out
     * or is declined while a second incoming call is still ringing, the second call must keep
     * sounding, so the tones are left running until the last ringing call ends.
     *
     * Only incoming calls that have not been answered count as ringing: [ringingIncomingCallIds]
     * excludes outgoing/dialing calls (which never play the ringtone) and [answeredCallIds]
     * excludes calls that are already active.
     */
    private fun stopRingtoneUnlessOtherCallRinging(terminatingCallId: String) {
        if (hasOtherRingingCall(ringingIncomingCallIds, answeredCallIds, terminatingCallId)) {
            Log.d(TAG, "stopRingtoneUnlessOtherCallRinging: keeping tone, another call still ringing")
            return
        }
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
    }

    private fun handleUpdateCall(metadata: CallMetadata) {
        Log.i(TAG, "handleUpdateCall: callId=${metadata.callId}")
        val updated = (callMetadataMap[metadata.callId] ?: metadata).mergeWith(metadata)
        callMetadataMap[metadata.callId] = updated
    }

    private fun handleSendDtmf(metadata: CallMetadata) {
        val dtmf = metadata.dualToneMultiFrequency ?: return
        Log.i(TAG, "handleSendDtmf: callId=${metadata.callId}, dtmf=$dtmf")
        val updated = (callMetadataMap[metadata.callId] ?: metadata).copy(dualToneMultiFrequency = dtmf)
        callMetadataMap[metadata.callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.SentDTMF, updated.toBundle())
    }

    private fun handleHolding(metadata: CallMetadata) {
        val onHold = metadata.hasHold ?: return
        Log.i(TAG, "handleHolding: callId=${metadata.callId}, onHold=$onHold")
        if (onHold) {
            audioManager.mode = AudioManager.MODE_NORMAL
        } else {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        val updated = (callMetadataMap[metadata.callId] ?: metadata).copy(hasHold = onHold)
        callMetadataMap[metadata.callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.ConnectionHolding, updated.toBundle())
        // No onStateChanged here (no telecom Connection) — emit the state explicitly so the shadow
        // mirrors it (replaces the removed markHeld state-stamping).
        val holdState = if (onHold) CallConnectionState.HOLDING else CallConnectionState.ACTIVE
        core.notifyConnectionEvent(
            CallLifecycleEvent.ConnectionStateChanged,
            updated.copy(connectionState = holdState).toBundle(),
        )
    }

    private fun handleTearDownConnections() {
        Log.i(TAG, "handleTearDownConnections: cleaning up ${callMetadataMap.size} calls")
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
        callMetadataMap.keys.toList().forEach { callId ->
            val meta = callMetadataMap[callId] ?: CallMetadata(callId = callId)
            core.notifyConnectionEvent(CallLifecycleEvent.HungUp, meta.toBundle())
        }
        callMetadataMap.clear()
        ringingIncomingCallIds.clear()
        answeredCallIds.clear()
        pendingAnswers.clear()
        deactivateAudio(force = true)
        core.notifyConnectionEvent(CallCommandEvent.TearDownComplete)
        stopSelf()
    }

    private fun handleCleanConnections() {
        Log.i(TAG, "handleCleanConnections: clearing state")
        callMetadataMap.clear()
        ringingIncomingCallIds.clear()
        answeredCallIds.clear()
        pendingAnswers.clear()
        deactivateAudio(force = true)
        ringtoneManager.stopRingtone()
        ringtoneManager.stopCallWaitingTone()
    }

    /**
     * Handles a deferred answer reservation for [callId].
     *
     * If the call has already been registered via [handleIncomingCall], answer it immediately.
     * If not yet registered (narrow race: ReserveAnswer arrived before IncomingCall was processed),
     * record the reservation so [handleIncomingCall] can apply it when it fires.
     */
    private fun handleReserveAnswer(callId: String) {
        Log.i(TAG, "handleReserveAnswer: callId=$callId")
        val meta = callMetadataMap[callId]
        if (meta != null) {
            handleAnswerCall(meta)
        } else {
            pendingAnswers.add(callId)
        }
    }

    private fun handleMuting(metadata: CallMetadata) {
        val muted = metadata.hasMute ?: return
        Log.i(TAG, "handleMuting: callId=${metadata.callId}, muted=$muted")
        audioManager.isMicrophoneMute = muted
        val updated =
            (callMetadataMap[metadata.callId] ?: metadata).copy(hasMute = muted)
        callMetadataMap[metadata.callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.AudioMuting, updated.toBundle())
    }

    private fun handleSpeaker(metadata: CallMetadata) {
        val speaker = metadata.hasSpeaker ?: return
        Log.i(TAG, "handleSpeaker: callId=${metadata.callId}, speaker=$speaker")
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = speaker
        val deviceType = if (speaker) AudioDeviceType.SPEAKER else AudioDeviceType.EARPIECE
        val updated =
            (callMetadataMap[metadata.callId] ?: metadata).copy(
                hasSpeaker = speaker,
                audioDevice = AudioDevice(deviceType),
            )
        callMetadataMap[metadata.callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.AudioDeviceSet, updated.toBundle())
    }

    private fun handleAudioDeviceSet(metadata: CallMetadata) {
        val device = metadata.audioDevice ?: return
        Log.i(TAG, "handleAudioDeviceSet: callId=${metadata.callId}, device=${device.type}")
        val isSpeaker = device.type == AudioDeviceType.SPEAKER
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = isSpeaker
        val updated =
            (callMetadataMap[metadata.callId] ?: metadata).copy(
                hasSpeaker = isSpeaker,
                audioDevice = device,
            )
        callMetadataMap[metadata.callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.AudioDeviceSet, updated.toBundle())
    }

    private fun handleSyncAudioState() {
        Log.i(TAG, "handleSyncAudioState: re-emitting audio state for answered calls")
        answeredCallIds.forEach { callId -> fireInitialAudioState(callId) }
    }

    private fun handleSyncConnectionState() {
        Log.i(TAG, "handleSyncConnectionState: re-emitting AnswerCall + ACTIVE state for answered calls")
        answeredCallIds.forEach { callId ->
            val meta = callMetadataMap[callId] ?: CallMetadata(callId = callId)
            core.notifyConnectionEvent(CallLifecycleEvent.AnswerCall, meta.toBundle())
            core.notifyConnectionEvent(
                CallLifecycleEvent.ConnectionStateChanged,
                meta.copy(connectionState = CallConnectionState.ACTIVE).toBundle(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Audio helpers
    // -------------------------------------------------------------------------

    private fun activateAudio() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun deactivateAudio(force: Boolean = false) {
        if (force || callMetadataMap.isEmpty()) {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
        }
    }

    /**
     * Fires [CallMediaEvent.AudioDevicesUpdate] and [CallMediaEvent.AudioDeviceSet] with
     * the available and currently selected audio device for [callId].
     *
     * Reported devices are limited to earpiece and speaker. Bluetooth and wired headset
     * detection requires an [android.media.AudioDeviceCallback] listener which can be
     * added in a follow-up when needed.
     */
    private fun fireInitialAudioState(callId: String) {
        val metadata = callMetadataMap[callId] ?: return
        val availableDevices =
            listOf(
                AudioDevice(AudioDeviceType.EARPIECE),
                AudioDevice(AudioDeviceType.SPEAKER),
            )
        val currentDevice = metadata.audioDevice ?: AudioDevice(AudioDeviceType.EARPIECE)
        val updated = metadata.copy(audioDevices = availableDevices, audioDevice = currentDevice)
        callMetadataMap[callId] = updated
        core.notifyConnectionEvent(CallMediaEvent.AudioDevicesUpdate, updated.toBundle())
        core.notifyConnectionEvent(CallMediaEvent.AudioDeviceSet, updated.toBundle())
    }

    private fun endCall(metadata: CallMetadata) {
        callMetadataMap.remove(metadata.callId)
        ringingIncomingCallIds.remove(metadata.callId)
        answeredCallIds.remove(metadata.callId)
        pendingAnswers.remove(metadata.callId)
        if (callMetadataMap.isEmpty()) {
            deactivateAudio()
            stopSelf()
        }
    }

    // -------------------------------------------------------------------------
    // Companion (static dispatch interface, mirrors PhoneConnectionService)
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "StandaloneCallService"

        // Arbitrary notification ID that does not collide with main-process notification IDs.
        private const val NOTIFICATION_ID = 97

        @Volatile
        var isRunning: Boolean = false
            private set

        // Call state shared within the :callkeep_core process JVM.
        // Written on the main thread (onStartCommand); read from static dispatch methods
        // called on the main process thread, hence ConcurrentHashMap.
        internal val callMetadataMap: ConcurrentHashMap<String, CallMetadata> = ConcurrentHashMap()

        // Incoming call ids, from registration until the call ends (added in handleIncomingCall,
        // removed in endCall / cleared on teardown+clean). It is NOT pruned when a call is answered,
        // so it may contain answered calls; "still ringing" is therefore membership here AND absence
        // from answeredCallIds (see hasOtherRingingCall). Distinct from callMetadataMap, which also
        // holds outgoing/dialing calls that never play the ringtone - hence the ringtone-stop guard
        // consults this set, not the full map, to decide whether another call is still ringing.
        internal val ringingIncomingCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
        internal val answeredCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
        internal val pendingAnswers: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /**
         * `true` when a call other than [excludingCallId] is still ringing, i.e. registered in
         * [ringingCallIds] but not present in [answeredCallIds]. Pure helper so the ringtone-stop
         * guard is unit-testable without instantiating the service.
         */
        internal fun hasOtherRingingCall(
            ringingCallIds: Set<String>,
            answeredCallIds: Set<String>,
            excludingCallId: String,
        ): Boolean = ringingCallIds.any { it != excludingCallId && it !in answeredCallIds }

        /**
         * Starts an incoming call in standalone mode.
         *
         * Reuses [ConnectionManager.validateConnectionAddition] (which operates on the
         * main-process [PhoneConnectionService.connectionManager] instance) for deduplication,
         * matching the same validation path used by [PhoneConnectionService.startIncomingCall].
         */
        fun startIncomingCall(
            context: Context,
            metadata: CallMetadata,
            onSuccess: () -> Unit,
            onError: (PIncomingCallError?) -> Unit,
        ) {
            ConnectionManager.validateConnectionAddition(
                metadata = metadata,
                onSuccess = {
                    val intent =
                        Intent(context, StandaloneCallService::class.java).apply {
                            action = StandaloneServiceAction.IncomingCall.action
                            putExtras(metadata.toBundle())
                        }
                    try {
                        context.startForegroundService(intent)
                        onSuccess()
                    } catch (e: Exception) {
                        Log.e(TAG, "startIncomingCall: startForegroundService failed for callId=${metadata.callId}", e)
                        PhoneConnectionService.connectionManager.removePending(metadata.callId)
                        onError(null)
                    }
                },
                onError = { error -> onError(error) },
            )
        }

        /**
         * Starts an outgoing call in standalone mode.
         *
         * Fires the service with [StandaloneServiceAction.OutgoingCall], which stores the call
         * metadata and broadcasts [CallLifecycleEvent.OngoingCall] back to the main process so
         * that [ForegroundService] can promote the call and notify the Flutter layer.
         *
         * The call is established (audio activated, [CallLifecycleEvent.AnswerCall] fired) when
         * the app later calls [startEstablishCall] via [StandaloneServiceAction.EstablishCall].
         */
        fun startOutgoingCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            val intent =
                Intent(context, StandaloneCallService::class.java).apply {
                    action = StandaloneServiceAction.OutgoingCall.action
                    putExtras(metadata.toBundle())
                }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startOutgoingCall: startForegroundService failed for callId=${metadata.callId}", e)
                throw e
            }
        }

        /**
         * Dispatches [action] to the running [StandaloneCallService] via [startService].
         *
         * If [StandaloneCallService] is not currently running (e.g. the OS killed the process
         * between the incoming call and the command), the call is silently dropped and, for
         * teardown commands, a [CallCommandEvent.TearDownComplete] ack is synthesised so that
         * [ForegroundService] does not wait indefinitely on its timeout.
         */
        fun communicate(
            context: Context,
            action: StandaloneServiceAction,
            metadata: CallMetadata?,
        ) {
            val intent =
                Intent(context, StandaloneCallService::class.java).apply {
                    this.action = action.action
                    metadata?.toBundle()?.let { putExtras(it) }
                }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "communicate: startService failed for action=${action.name}: $e")
                // Synthesise a TearDownComplete ack so ForegroundService.tearDown() does not
                // block on a timeout when the service is no longer alive.
                if (action == StandaloneServiceAction.TearDownConnections) {
                    CallkeepCore.instance.notifyConnectionEvent(CallCommandEvent.TearDownComplete)
                }
            }
        }

        fun tearDown(context: Context) {
            communicate(context, StandaloneServiceAction.TearDownConnections, null)
        }
    }
}

/**
 * Action strings used for routing commands inside [StandaloneCallService.onStartCommand].
 *
 * Mirrors [ServiceAction] but scoped exclusively to [StandaloneCallService] so that
 * intents targeting the standalone path cannot accidentally be routed into
 * [PhoneConnectionService.onStartCommand] and vice versa.
 */
enum class StandaloneServiceAction {
    IncomingCall,
    OutgoingCall,
    EstablishCall,
    AnswerCall,
    DeclineCall,
    HungUpCall,
    UpdateCall,
    SendDtmf,
    Holding,
    TearDownConnections,
    CleanConnections,
    ReserveAnswer,
    Muting,
    Speaker,
    AudioDeviceSet,
    SyncAudioState,
    SyncConnectionState,
    ;

    val action: String get() = "callkeep_standalone_$name"

    /**
     * Call-setup actions are the only ones started via [android.content.Context.startForegroundService]
     * (see [StandaloneCallService.startIncomingCall] / [StandaloneCallService.startOutgoingCall]) and
     * therefore impose the 5-second [android.app.Service.startForeground] window. Single source of
     * truth used by [StandaloneCallService.onStartCommand] to promote to foreground from the raw
     * action, independently of whether the command metadata parses.
     */
    val isCallSetup: Boolean get() = this == IncomingCall || this == OutgoingCall

    companion object {
        fun from(action: String?): StandaloneServiceAction? = entries.find { it.action == action }
    }
}
