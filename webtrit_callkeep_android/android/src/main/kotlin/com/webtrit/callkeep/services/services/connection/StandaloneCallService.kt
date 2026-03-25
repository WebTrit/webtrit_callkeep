package com.webtrit.callkeep.services.services.connection

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.CallCommandEvent
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import java.util.concurrent.ConcurrentHashMap

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
 * The service runs in the `:callkeep_core` process, mirroring [PhoneConnectionService].
 * It is started as a foreground service on the first incoming call and stopped when all
 * calls have ended or a teardown command is received.
 */
@Keep
class StandaloneCallService : Service() {
    private val dispatcher = ConnectionServicePerformBroadcaster.handle
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(applicationContext)
        AssetCacheManager.init(applicationContext)
        isRunning = true

        // Satisfy Android's 5-second startForeground() requirement immediately.
        // The actual visible incoming-call notification is shown by IncomingCallService in the
        // main process. This is a minimal placeholder required only to keep the :callkeep_core
        // process alive for the duration of the call.
        val placeholder =
            Notification
                .Builder(this, NotificationChannelManager.FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .build()
        startForegroundServiceCompat(
            this,
            NOTIFICATION_ID,
            placeholder,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action =
            intent?.action?.let { StandaloneServiceAction.from(it) } ?: run {
                Log.w(TAG, "onStartCommand: unknown or missing action '${intent?.action}', ignoring")
                return START_NOT_STICKY
            }
        val metadata = intent.extras?.let { CallMetadata.fromBundle(it) }

        try {
            when (action) {
                StandaloneServiceAction.IncomingCall -> metadata?.let { handleIncomingCall(it) }
                StandaloneServiceAction.OutgoingCall -> metadata?.let { handleOutgoingCall(it) }
                StandaloneServiceAction.EstablishCall -> metadata?.let { handleEstablishCall(it) }
                StandaloneServiceAction.AnswerCall -> metadata?.let { handleAnswerCall(it) }
                StandaloneServiceAction.DeclineCall -> metadata?.let { handleDeclineCall(it) }
                StandaloneServiceAction.HungUpCall -> metadata?.let { handleHungUpCall(it) }
                StandaloneServiceAction.TearDownConnections -> handleTearDownConnections()
                StandaloneServiceAction.CleanConnections -> handleCleanConnections()
                StandaloneServiceAction.ReserveAnswer -> metadata?.callId?.let { handleReserveAnswer(it) }
                StandaloneServiceAction.Muting -> metadata?.let { handleMuting(it) }
                StandaloneServiceAction.Speaker -> metadata?.let { handleSpeaker(it) }
                StandaloneServiceAction.AudioDeviceSet -> metadata?.let { handleAudioDeviceSet(it) }
                StandaloneServiceAction.SyncAudioState -> handleSyncAudioState()
                StandaloneServiceAction.SyncConnectionState -> handleSyncConnectionState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception $e with action: ${intent?.action}")
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    private fun handleIncomingCall(metadata: CallMetadata) {
        Log.i(TAG, "handleIncomingCall: callId=${metadata.callId}")
        callMetadataMap[metadata.callId] = metadata
        answeredCallIds.remove(metadata.callId)
        // Notify the main process that the call has been registered. ForegroundService listens
        // for this broadcast to resolve its pendingIncomingCallbacks entry and promote the call
        // into the core shadow state, matching the PhoneConnectionService.onCreateIncomingConnection
        // path in the Telecom-enabled flow.
        dispatcher.dispatch(baseContext, CallLifecycleEvent.DidPushIncomingCall, metadata.toBundle())
    }

    private fun handleOutgoingCall(metadata: CallMetadata) {
        Log.i(TAG, "handleOutgoingCall: callId=${metadata.callId}")
        callMetadataMap[metadata.callId] = metadata
        answeredCallIds.remove(metadata.callId)
        // Notify the main process that the outgoing call is in progress, mirroring the
        // OngoingCall broadcast that PhoneConnectionService fires after onCreateOutgoingConnection.
        // ForegroundService listens for this to promote the call to STATE_DIALING and call performStartCall.
        dispatcher.dispatch(baseContext, CallLifecycleEvent.OngoingCall, metadata.toBundle())
    }

    private fun handleEstablishCall(metadata: CallMetadata) {
        Log.i(TAG, "handleEstablishCall: callId=${metadata.callId}")
        val full = (callMetadataMap[metadata.callId] ?: metadata).mergeWith(metadata)
        callMetadataMap[metadata.callId] = full.copy(acceptedTime = System.currentTimeMillis())
        answeredCallIds.add(metadata.callId)
        pendingAnswers.remove(metadata.callId)

        activateAudio()
        fireInitialAudioState(metadata.callId)

        dispatcher.dispatch(
            baseContext,
            CallLifecycleEvent.AnswerCall,
            callMetadataMap[metadata.callId]!!.toBundle(),
        )
    }

    private fun handleAnswerCall(metadata: CallMetadata) {
        Log.i(TAG, "handleAnswerCall: callId=${metadata.callId}")
        val full = (callMetadataMap[metadata.callId] ?: metadata).mergeWith(metadata)
        callMetadataMap[metadata.callId] = full.copy(acceptedTime = System.currentTimeMillis())
        answeredCallIds.add(metadata.callId)
        pendingAnswers.remove(metadata.callId)

        activateAudio()
        fireInitialAudioState(metadata.callId)

        dispatcher.dispatch(
            baseContext,
            CallLifecycleEvent.AnswerCall,
            callMetadataMap[metadata.callId]!!.toBundle(),
        )
    }

    private fun handleDeclineCall(metadata: CallMetadata) {
        Log.i(TAG, "handleDeclineCall: callId=${metadata.callId}")
        endCall(metadata)
        dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, metadata.toBundle())
    }

    private fun handleHungUpCall(metadata: CallMetadata) {
        Log.i(TAG, "handleHungUpCall: callId=${metadata.callId}")
        endCall(metadata)
        dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, metadata.toBundle())
    }

    private fun handleTearDownConnections() {
        Log.i(TAG, "handleTearDownConnections: cleaning up ${callMetadataMap.size} calls")
        callMetadataMap.keys.toList().forEach { callId ->
            val meta = callMetadataMap[callId] ?: CallMetadata(callId = callId)
            dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, meta.toBundle())
        }
        callMetadataMap.clear()
        answeredCallIds.clear()
        pendingAnswers.clear()
        deactivateAudio(force = true)
        dispatcher.dispatch(baseContext, CallCommandEvent.TearDownComplete)
        stopSelf()
    }

    private fun handleCleanConnections() {
        Log.i(TAG, "handleCleanConnections: clearing state")
        callMetadataMap.clear()
        answeredCallIds.clear()
        pendingAnswers.clear()
        deactivateAudio(force = true)
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
        dispatcher.dispatch(baseContext, CallMediaEvent.AudioMuting, updated.toBundle())
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
        dispatcher.dispatch(baseContext, CallMediaEvent.AudioDeviceSet, updated.toBundle())
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
        dispatcher.dispatch(baseContext, CallMediaEvent.AudioDeviceSet, updated.toBundle())
    }

    private fun handleSyncAudioState() {
        Log.i(TAG, "handleSyncAudioState: re-emitting audio state for answered calls")
        answeredCallIds.forEach { callId -> fireInitialAudioState(callId) }
    }

    private fun handleSyncConnectionState() {
        Log.i(TAG, "handleSyncConnectionState: re-emitting AnswerCall for answered calls")
        answeredCallIds.forEach { callId ->
            val meta = callMetadataMap[callId] ?: CallMetadata(callId = callId)
            dispatcher.dispatch(baseContext, CallLifecycleEvent.AnswerCall, meta.toBundle())
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
        dispatcher.dispatch(baseContext, CallMediaEvent.AudioDevicesUpdate, updated.toBundle())
        dispatcher.dispatch(baseContext, CallMediaEvent.AudioDeviceSet, updated.toBundle())
    }

    private fun endCall(metadata: CallMetadata) {
        callMetadataMap.remove(metadata.callId)
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
        internal val answeredCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
        internal val pendingAnswers: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
                    ConnectionServicePerformBroadcaster.handle.dispatch(
                        context,
                        CallCommandEvent.TearDownComplete,
                    )
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

    companion object {
        fun from(action: String?): StandaloneServiceAction? = entries.find { it.action == action }
    }
}
