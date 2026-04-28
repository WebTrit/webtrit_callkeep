package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.os.ParcelUuid
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.managers.AudioManager
import com.webtrit.callkeep.managers.NotificationManager
import com.webtrit.callkeep.models.AudioDevice
import com.webtrit.callkeep.models.AudioDeviceType
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import java.util.concurrent.Executors

/**
 * Manages an individual phone connection, handling state transitions and audio routing.
 */
class PhoneConnection internal constructor(
    private val context: Context,
    private val dispatcher: PerformDispatchHandle,
    private var metadata: CallMetadata,
    var onDisconnectCallback: (connection: PhoneConnection) -> Unit,
    var timeout: ConnectionTimeout? = null,
    private val audioManager: AudioManager = AudioManager(context),
) : Connection() {
    private var isMute = false
    private var isHasSpeaker = false

    /**
     * Tracks whether the speaker was manually disabled by the user.
     * Prevents automatic re-enabling during video calls.
     */
    private var isSpeakerManuallyDisabled = false

    /**
     * Prevents automatic speaker enforcement if the call originally started as audio-only.
     * This ensures mid-call video upgrades from the remote party do not force the speaker on.
     */
    private var preventAutoSpeakerEnforcement = false

    /**
     * Tracks a pending [CallEndpoint] change request, ensuring only one is active at a time.
     */
    @Volatile
    private var pendingEndpointRequest: CallEndpoint? = null
    private var availableCallEndpoints: List<CallEndpoint> = emptyList()
    private val notificationManager = NotificationManager()

    private var lastKnownState: Int? = null

    val callId: String
        get() = metadata.callId

    val displayName: String?
        get() = metadata.displayName

    val handle: com.webtrit.callkeep.models.CallHandle?
        get() = metadata.handle

    val hasVideo: Boolean
        get() = metadata.hasVideo ?: false

    val isSpeakerOnVideoEnabled: Boolean
        get() = metadata.speakerOnVideo ?: true

    val proximityEnabled: Boolean
        get() = metadata.proximityEnabled ?: false

    val hasMute: Boolean
        get() = isMute

    val hasHold: Boolean
        get() = state == STATE_HOLDING

    var hasAnswered: Boolean = false
        private set

    init {
        audioModeIsVoip = true
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD

        setInitializing()
        updateData(metadata)
    }

    /**
     * Transitions the connection to an active state and ensures the UI is visible.
     */
    fun establish() {
        logger.d("Establishing connection for callId: $callId")
        context.startActivity(Platform.getLaunchActivity(context))
        setActive()
    }

    /**
     * Synchronizes the internal mute state and notifies the application.
     */
    fun changeMuteState(muted: Boolean) {
        logger.d("Changing mute state to: $muted for callId: $callId")
        this.isMute = muted
        dispatcher(CallMediaEvent.AudioMuting, metadata.copy(hasMute = this.isMute))
    }

    /**
     * Invoked by the system when the incoming call interface should be displayed.
     *
     * If there is already an active or held call, play a soft call-waiting tone through
     * the voice call stream instead of the full ringtone. The ringtone uses TYPE_RINGTONE
     * which routes through the earpiece at full ringtone volume during an active call,
     * and can cause pain if the user has the phone pressed to their ear (WT-1388).
     */
    override fun onShowIncomingCallUi() {
        logger.d("Showing incoming call UI for callId: $callId")
        notificationManager.showIncomingCallNotification(metadata)
        if (PhoneConnectionService.connectionManager.hasActiveOrHoldingConnection()) {
            logger.d("Active call detected — playing call-waiting tone instead of ringtone for callId: $callId")
            audioManager.startCallWaitingTone()
        } else {
            audioManager.startRingtone(metadata.ringtonePath)
        }
        dispatcher(CallLifecycleEvent.DidPushIncomingCall, metadata)
    }

    /**
     * Invoked by Telecom when the user presses a volume key during an incoming call.
     *
     * For self-managed calls, Telecom does not control the ringtone directly — it delegates
     * silence requests to the app via this callback. Without this override the ringtone keeps
     * playing regardless of volume key presses (confirmed on Xiaomi/MIUI and Samsung/One UI,
     * Android 11, WT-1300).
     */
    override fun onSilence() {
        logger.d("Silencing ringtone for callId: $callId")
        audioManager.stopRingtone()
        audioManager.stopCallWaitingTone()
    }

    /**
     * Handles the transition when a user accepts an incoming call.
     */
    override fun onAnswer() {
        logger.i("Answering call: $metadata")
        super.onAnswer()
        hasAnswered = true
        setActive()
        dispatcher(CallLifecycleEvent.AnswerCall, metadata)
        ActivityHolder.start(context)
    }

    /**
     * Handles the transition when a user rejects an incoming call.
     */
    override fun onReject() {
        logger.i("Rejecting call: $callId")
        super.onReject()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    }

    /**
     * Performs final cleanup when the connection is terminated.
     */
    override fun onDisconnect() {
        logger.i("Disconnecting call: $callId")
        super.onDisconnect()

        timeout?.cancel()
        notificationManager.cancelIncomingNotification(hasAnswered)
        notificationManager.cancelActiveCallNotification(callId)
        audioManager.stopRingtone()
        audioManager.stopCallWaitingTone()

        dispatcher(eventForDisconnectCause(disconnectCause), metadata)
        onDisconnectCallback.invoke(this)
        destroy()
    }

    /**
     * Updates the internal state to reflect a held call.
     */
    override fun onHold() {
        logger.d("Putting call on hold: $callId")
        super.onHold()
        setOnHold()
        dispatcher(CallMediaEvent.ConnectionHolding, metadata.copy(hasHold = true))
    }

    /**
     * Resumes the call from a held state.
     */
    override fun onUnhold() {
        logger.d("Taking call off hold: $callId")
        super.onUnhold()
        setActive()
        dispatcher(CallMediaEvent.ConnectionHolding, metadata.copy(hasHold = false))
    }

    /**
     * Dispatches a DTMF tone event to the application.
     */
    override fun onPlayDtmfTone(c: Char) {
        logger.d("Playing DTMF tone: $c for callId: $callId")
        super.onPlayDtmfTone(c)
        dispatcher(CallMediaEvent.SentDTMF, metadata.copy(dualToneMultiFrequency = c))
    }

    /**
     * Orchestrates logical transitions based on the underlying Telecom state.
     */
    override fun onStateChanged(state: Int) {
        val stateText =
            when (state) {
                STATE_NEW -> "NEW"
                STATE_DIALING -> "DIALING"
                STATE_RINGING -> "RINGING"
                STATE_HOLDING -> "HOLDING"
                STATE_ACTIVE -> "ACTIVE"
                STATE_DISCONNECTED -> "DISCONNECTED"
                else -> "UNKNOWN($state)"
            }
        logger.v("Connection state is now: $stateText for callId: $callId")
        super.onStateChanged(state)
        handleConnectionTimeout(state)

        if (lastKnownState == STATE_NEW && state == STATE_DIALING) {
            onDialing()
        }

        // Core Fix: Ensure onActiveConnection is called when transitioning from transient states (DIALING/RINGING) to ACTIVE and ignore Hold -> Unhold
        if ((lastKnownState == STATE_DIALING || lastKnownState == STATE_RINGING) && state == STATE_ACTIVE) {
            onActiveConnection()
        }

        lastKnownState = state
    }

    /**
     * Manages automatic disconnection logic if the call stays in transient states too long.
     */
    private fun handleConnectionTimeout(state: Int) {
        if (state in timeout?.states.orEmpty()) {
            timeout?.start(::onTimeoutTriggered)
        } else {
            timeout?.cancel()
        }
    }

    /**
     * Executed when the [ConnectionTimeout] timer elapses.
     */
    private fun onTimeoutTriggered() {
        logger.w("Timeout reached for callId: $callId")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED, "Timeout reached"))
        onDisconnect()
    }

    /**
     * Handles audio routing changes for Android versions below API 34.
     */
    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        logger.d("Legacy audio state changed: $state")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        val audioDevices = state?.supportedRouteMask?.let(::mapSupportedRoutes) ?: emptyList()
        dispatcher(CallMediaEvent.AudioDevicesUpdate, metadata.copy(audioDevices = audioDevices))

        val currentDevice = state?.route?.let(::mapRouteToAudioDevice) ?: AudioDevice(AudioDeviceType.UNKNOWN)
        dispatcher(CallMediaEvent.AudioDeviceSet, metadata.copy(audioDevice = currentDevice))
    }

    /**
     * Refreshes the audio state and endpoints for the application layer.
     */
    fun forceUpdateAudioState() {
        logger.d("Force updating audio state for callId: $callId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            updateModernAudioState()
        } else {
            val state = callAudioState
            if (state != null) {
                onCallAudioStateChanged(state)
            } else {
                // Fallback for OEM Telecom implementations (e.g. MIUI Android 12) that do not
                // call setCallAudioState for outgoing self-managed calls. On AOSP, the
                // CallAudioRouteStateMachine.resendSystemAudioState() seeds this during call
                // setup; MIUI's MiuiCallsManager skips that step for outgoing calls.
                dispatchFallbackAudioState()
            }
        }
    }

    /**
     * Builds and dispatches audio device information from [android.media.AudioManager] directly.
     *
     * Used as a fallback when [callAudioState] was never set by the Telecom framework (e.g.
     * on MIUI Android 12 for outgoing calls). Earpiece and Speaker are always present on a
     * phone; wired and Bluetooth headsets are detected via input-device availability.
     */
    private fun dispatchFallbackAudioState() {
        logger.w("dispatchFallbackAudioState: callAudioState not set by system — building device list from AudioManager")
        val supportedDevices =
            buildList {
                if (audioManager.isSupportEarpiese()) add(AudioDevice(AudioDeviceType.EARPIECE))
                if (audioManager.isSupportSpeakerphone()) add(AudioDevice(AudioDeviceType.SPEAKER))
                if (audioManager.isWiredHeadsetConnected()) add(AudioDevice(AudioDeviceType.WIRED_HEADSET))
                if (audioManager.isBluetoothConnected()) add(AudioDevice(AudioDeviceType.BLUETOOTH))
            }
        dispatcher(CallMediaEvent.AudioDevicesUpdate, metadata.copy(audioDevices = supportedDevices))

        val currentDevice =
            when {
                audioManager.isBluetoothConnected() -> AudioDevice(AudioDeviceType.BLUETOOTH)
                audioManager.isWiredHeadsetConnected() -> AudioDevice(AudioDeviceType.WIRED_HEADSET)
                audioManager.isSpeakerphoneOn() -> AudioDevice(AudioDeviceType.SPEAKER)
                else -> AudioDevice(AudioDeviceType.EARPIECE)
            }
        dispatcher(CallMediaEvent.AudioDeviceSet, metadata.copy(audioDevice = currentDevice))
    }

    /**
     * Triggers modern endpoint updates for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateModernAudioState() {
        val endpoint = currentCallEndpoint ?: return
        onCallEndpointChanged(endpoint)
        if (availableCallEndpoints.isNotEmpty()) {
            onAvailableCallEndpointsChanged(availableCallEndpoints)
        }
        dispatcher(CallMediaEvent.AudioMuting, metadata.copy(hasMute = isMute))
    }

    /**
     * Updates available audio destinations for API 34+.
     *
     * This method intercepts the initial hardware report to resolve the "Sticky Speaker State."
     * On some devices, the Telecom Framework caches the last used route. By forcing the
     * EARPIECE during the first load of an audio-only call, we override this behavior.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(callEndpoints: List<CallEndpoint>) {
        val isFirstLoad = availableCallEndpoints.isEmpty()
        super.onAvailableCallEndpointsChanged(callEndpoints)
        logger.d("Available call endpoints changed: $callEndpoints")
        availableCallEndpoints = callEndpoints

        val devices = callEndpoints.map(::mapEndpointToAudioDevice)
        dispatcher(CallMediaEvent.AudioDevicesUpdate, metadata.copy(audioDevices = devices))

        try {
            /*
             * Core Fix: Force EARPIECE on initialization for audio-only calls.
             * By forcing the switch blindly on the first load, we preemptively correct
             * any "sticky" speaker state without risking a crash.
             */
            if (isFirstLoad && !hasVideo) {
                val earpiece = callEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
                if (earpiece != null) {
                    logger.i("Startup: Preemptively forcing EARPIECE to clear sticky state.")
                    performEndpointChange(earpiece)
                }
            }
        } catch (e: Exception) {
            /*
             * Defensive logging: Ensures the call remains active even if
             * the platform-specific routing request fails.
             */
            logger.w("Failed to perform initial audio endpoint correction: ${e.message}", e)
        }

        enforceVideoSpeakerLogic()
    }

    /**
     * Updates the currently active audio destination for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        logger.i("Call endpoint changed to: $callEndpoint")
        val device = mapEndpointToAudioDevice(callEndpoint)
        dispatcher(CallMediaEvent.AudioDeviceSet, metadata.copy(audioDevice = device))

        isHasSpeaker = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER
        // Guard against the system automatically switching back to Earpiece/Wired.
        // If the system forces a switch during a video call, we force it back.
        enforceVideoSpeakerLogic()
    }

    /**
     * Syncs system mute state changes for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        logger.d("Mute state changed via system: $isMuted")
        isMute = isMuted
        dispatcher(CallMediaEvent.AudioMuting, metadata.copy(hasMute = isMute))
    }

    /**
     * Requests a change to a specific audio device.
     */
    fun setAudioDevice(device: AudioDevice) {
        logger.i("Setting audio device: $device for callId: $callId")

        isSpeakerManuallyDisabled = device.type != AudioDeviceType.SPEAKER

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val deviceId = device.id
            if (deviceId == null) {
                logger.e("Cannot set audio device: null device id. Requested device: $device, callId: $callId")
                return
            }

            val endpoint =
                availableCallEndpoints.firstOrNull {
                    it.identifier == ParcelUuid.fromString(deviceId)
                }

            if (endpoint != null) {
                performEndpointChange(endpoint)
            } else {
                logger.e(
                    "No suitable call endpoint found for the current audio state. Requested device: $device, callId: $callId",
                )
            }
        } else {
            val targetRoute = mapDeviceTypeToRoute(device.type)
            setAudioRoute(targetRoute)

            // MIUI Android 12 (and similar OEM Telecom implementations) silently ignores
            // setAudioRoute() — both for outgoing calls (where callAudioState is always null)
            // and for incoming calls after hold/unhold (where MIUI re-seeds callAudioState
            // when ACTIVE is re-confirmed, making callAudioState non-null, yet still ignores
            // setAudioRoute()). Always bypass Telecom and route directly via AudioManager.
            // On AOSP, setAudioRoute() works and onCallAudioStateChanged fires authoritatively
            // to override the proactive dispatch below — idempotent.

            directRouteAudioDevice(device.type)
            dispatcher(CallMediaEvent.AudioDeviceSet, metadata.copy(audioDevice = device))
            isHasSpeaker = device.type == AudioDeviceType.SPEAKER
        }
    }

    /**
     * Toggles the speakerphone state.
     *
     * Consolidates logic for selecting the appropriate audio device based on whether
     * the speaker is being enabled or disabled, handling both Legacy and Modern (API 34+) paths.
     */
    fun toggleSpeaker(isActive: Boolean) {
        logger.d("Toggling speaker state: $isActive for callId: $callId")

        isSpeakerManuallyDisabled = !isActive

        val targetDevice: AudioDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                findSpeakerEndpoint(isActive)?.let(::mapEndpointToAudioDevice)
            } else {
                val route = determineLegacyRoute(isActive)
                mapRouteToAudioDevice(route)
            }

        if (targetDevice != null) {
            setAudioDevice(targetDevice)
        } else {
            logger.w("Could not resolve target device for speaker state: $isActive")
        }
    }

    /**
     * Centralized logic to enforce speakerphone for video calls.
     * Checks requirements: Video enabled, not ringing, and NO Bluetooth connected.
     */
    private fun enforceVideoSpeakerLogic() {
        logger.d(
            "enforceVideoSpeakerLogic: CHECKING... [hasVideo=$hasVideo, state=$state, isSpeakerOnVideoEnabled=$isSpeakerOnVideoEnabled, isHasSpeaker=$isHasSpeaker]",
        )

        // Exit immediately if this behavior is disabled in metadata
        if (!isSpeakerOnVideoEnabled) {
            logger.d("enforceVideoSpeakerLogic: SKIP -> Feature disabled in metadata (isSpeakerOnVideoEnabled=false)")
            return
        }

        // Must be video, must not be just ringing (incoming)
        if (!hasVideo || state == STATE_RINGING) {
            logger.d(
                "enforceVideoSpeakerLogic: SKIP -> Condition failed: hasVideo=$hasVideo (needs true) OR state=$state (needs != STATE_RINGING/2)",
            )
            return
        }

        // Prevent log warning on API 34 if endpoints aren't loaded yet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && availableCallEndpoints.isEmpty()) {
            logger.d("enforceVideoSpeakerLogic: SKIP -> API 34+ and endpoints not loaded yet")
            return
        }

        // Bluetooth Guard: If Bluetooth is available/connected, prefer it over Speaker.
        if (isBluetoothAvailable()) {
            logger.d("enforceVideoSpeakerLogic: SKIP -> Bluetooth device detected.")
            return
        }

        // Guard: If the call originally started as an audio-only session, we must not
        // abruptly switch to the speakerphone when a remote video upgrade occurs.
        // The user is likely still holding the phone to their ear.
        if (preventAutoSpeakerEnforcement) {
            logger.d("enforceVideoSpeakerLogic: SKIP -> Call started as audio. Ignoring mid-call video upgrade.")
            return
        }

        if (isSpeakerManuallyDisabled) {
            logger.d("enforceVideoSpeakerLogic: SKIP -> User manually disabled speaker")
            return
        }

        if (!isHasSpeaker) {
            logger.i("enforceVideoSpeakerLogic: ACTION -> Enforcing speaker for video call. State: $state")
            toggleSpeaker(true)
        } else {
            logger.d("enforceVideoSpeakerLogic: SKIP -> Speaker is already active (isHasSpeaker=true)")
        }
    }

    /**
     * Helper to detect if a Bluetooth audio device is available/connected.
     */
    private fun isBluetoothAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: Check if any endpoint is Bluetooth
            availableCallEndpoints.any { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        } else {
            // Legacy: Check supported routes from CallAudioState
            val supportedMask = callAudioState?.supportedRouteMask ?: 0
            (supportedMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        }

    /**
     * Updates call identity and visual parameters.
     */
    fun updateData(requestCallMetadata: CallMetadata) {
        logger.d(
            "updateData called with: hasVideo=${requestCallMetadata.hasVideo}, speakerOnVideo=${requestCallMetadata.speakerOnVideo}",
        )

        val previousHasVideo = metadata.hasVideo

        metadata = metadata.mergeWith(requestCallMetadata)
        extras = metadata.toBundle()

        setAddress(metadata.number.toUri(), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)

        if (previousHasVideo != metadata.hasVideo) {
            metadata.hasVideo?.let { applyVideoState(it) }
        }
    }

    /**
     * Terminates the connection from the local side before acceptance.
     */
    fun declineCall() {
        logger.d("Local decline for callId: $callId")
        terminateWithCause(DisconnectCause(DisconnectCause.REMOTE))
    }

    /**
     * Terminates the active connection.
     */
    fun hungUp() {
        logger.d("Local hang up for callId: $callId")
        dispatcher(CallMediaEvent.AudioMuting, metadata.copy(hasMute = isMute))
        terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))
    }

    /**
     * Logic triggered when the call enters an active talking state.
     */
    private fun onActiveConnection() {
        logger.i("Connection became active for callId: $callId")
        audioManager.stopRingtone()
        audioManager.stopCallWaitingTone()
        // IncomingCallService release (IC_RELEASE_WITH_ANSWER) is intentionally NOT triggered
        // here from :callkeep_core. ForegroundService.handleCSReportAnswerCall() owns that
        // trigger and sets pendingReleaseCallback before firing it, ensuring performAnswerCall
        // reaches the main Flutter engine only after the background isolate confirms its
        // signaling WebSocket is closed. Triggering release from both places would race and
        // risk pendingReleaseCallback being null when the isolate acks.
        notificationManager.showActiveCallNotification(callId, metadata)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            val update = metadata.copy(hasMute = isMute)
            dispatcher(CallMediaEvent.AudioMuting, update)
        }

        // If the incoming call is answered as audio-only, we set a flag to prevent
        // the speaker from turning on automatically if the remote party adds video later.
        // This prevents blasting audio into the user's ear.
        if (!hasVideo) {
            preventAutoSpeakerEnforcement = true
        }

        enforceVideoSpeakerLogic()

        // Proactively emit audio device state for OEM Telecom implementations that do not
        // call setCallAudioState for outgoing calls (e.g. MIUI Android 12). On AOSP,
        // CallAudioRouteStateMachine.resendSystemAudioState() fires during call setup and
        // triggers onCallAudioStateChanged; on MIUI that step is skipped for outgoing calls,
        // leaving callAudioState null and audio devices undiscovered until the next routing
        // event. forceUpdateAudioState() falls back to AudioManager enumeration when null.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            forceUpdateAudioState()
        }
    }

    /**
     * Logic triggered when the local side starts dialing.
     */
    private fun onDialing() {
        logger.i("Dialing callId: $callId")
        dispatcher(CallLifecycleEvent.OngoingCall, metadata)

        // If the outgoing call is initiated as audio-only, we prevent the speaker
        // from being forced on if the remote party answers with video or upgrades mid-call.
        if (!hasVideo) {
            preventAutoSpeakerEnforcement = true
        }

        enforceVideoSpeakerLogic()
    }

    /**
     * Updates the video provider and profile based on session requirements.
     */
    private fun applyVideoState(hasVideo: Boolean) {
        if (hasVideo) {
            videoProvider = PhoneVideoProvider()
            videoState = VideoProfile.STATE_BIDIRECTIONAL

            // Immediately enforce speaker if the user upgrades to video during an active call.
            enforceVideoSpeakerLogic()
        } else {
            videoProvider = null
            videoState = VideoProfile.STATE_AUDIO_ONLY
        }
    }

    /**
     * Executes an asynchronous endpoint switch for API 34+.
     *
     * Prevents duplicate requests and potential race conditions by tracking the
     * [pendingEndpointRequest] and ensuring only one active transition per
     * endpoint occurs at a time.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun performEndpointChange(endpoint: CallEndpoint) {
        synchronized(this) {
            if (pendingEndpointRequest?.identifier == endpoint.identifier) {
                logger.d("Skipping duplicate endpoint change request for endpoint: ${endpoint.identifier}")
                return
            }
            pendingEndpointRequest = endpoint
        }
        requestCallEndpointChange(
            endpoint,
            audioEndpointChangeExecutor,
            EndpointChangeReceiver(endpoint),
        )
    }

    /**
     * Converts a [CallEndpoint] into a domain [AudioDevice] model.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun mapEndpointToAudioDevice(endpoint: CallEndpoint) =
        AudioDevice(
            type =
                when (endpoint.endpointType) {
                    CallEndpoint.TYPE_EARPIECE -> AudioDeviceType.EARPIECE
                    CallEndpoint.TYPE_SPEAKER -> AudioDeviceType.SPEAKER
                    CallEndpoint.TYPE_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    CallEndpoint.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    CallEndpoint.TYPE_STREAMING -> AudioDeviceType.STREAMING
                    else -> AudioDeviceType.UNKNOWN
                },
            name = endpoint.endpointName.toString(),
            id = endpoint.identifier.toString(),
        )

    /**
     * Converts a [CallAudioState] route into a domain [AudioDevice] model.
     */
    private fun mapRouteToAudioDevice(route: Int) =
        AudioDevice(
            type =
                when (route) {
                    CallAudioState.ROUTE_EARPIECE -> AudioDeviceType.EARPIECE
                    CallAudioState.ROUTE_SPEAKER -> AudioDeviceType.SPEAKER
                    CallAudioState.ROUTE_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    CallAudioState.ROUTE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    CallAudioState.ROUTE_STREAMING -> AudioDeviceType.STREAMING
                    else -> AudioDeviceType.UNKNOWN
                },
        )

    /**
     * Parses the supported route mask into a list of [AudioDevice] models.
     */
    private fun mapSupportedRoutes(mask: Int) =
        listOfNotNull(
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) AudioDevice(AudioDeviceType.EARPIECE) else null,
            if (mask and CallAudioState.ROUTE_SPEAKER != 0) AudioDevice(AudioDeviceType.SPEAKER) else null,
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) AudioDevice(AudioDeviceType.BLUETOOTH) else null,
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) AudioDevice(AudioDeviceType.WIRED_HEADSET) else null,
            if (mask and CallAudioState.ROUTE_STREAMING != 0) AudioDevice(AudioDeviceType.STREAMING) else null,
        )

    /**
     * Maps domain device types back to Telecom route integers.
     */
    private fun mapDeviceTypeToRoute(type: AudioDeviceType) =
        when (type) {
            AudioDeviceType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            AudioDeviceType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioDeviceType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioDeviceType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }

    /**
     * Routes audio hardware directly via [android.media.AudioManager], bypassing Telecom.
     *
     * Required for OEM Telecom implementations (e.g. MIUI Android 12) that silently ignore
     * [Connection.setAudioRoute] for self-managed calls.
     *
     * [android.media.AudioManager.setCommunicationDevice] (API 31+) only routes audio when the
     * stack is in [android.media.AudioManager.MODE_IN_COMMUNICATION] (VoIP). On MIUI Android 12,
     * outgoing self-managed calls are misclassified as cellular (`IS_IPCALL=false` in the
     * ConnectionRequest extras), so MIUI runs audio in [android.media.AudioManager.MODE_IN_CALL]
     * instead — making [android.media.AudioManager.setCommunicationDevice] a no-op.
     *
     * [android.media.AudioManager.isSpeakerphoneOn] works in both
     * [android.media.AudioManager.MODE_IN_CALL] and
     * [android.media.AudioManager.MODE_IN_COMMUNICATION] and is used as the reliable fallback.
     */
    private fun directRouteAudioDevice(type: AudioDeviceType) {
        val sysAm = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mode = sysAm.mode
        logger.d("directRouteAudioDevice: type=$type, audioMode=$mode")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mode == android.media.AudioManager.MODE_IN_COMMUNICATION) {
            val targetType =
                when (type) {
                    AudioDeviceType.SPEAKER -> {
                        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }

                    AudioDeviceType.EARPIECE -> {
                        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }

                    AudioDeviceType.BLUETOOTH -> {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }

                    AudioDeviceType.WIRED_HEADSET -> {
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                    }

                    else -> {
                        logger.w("directRouteAudioDevice: unsupported type=$type, skipping")
                        return
                    }
                }
            val deviceInfo = sysAm.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == targetType }
            if (deviceInfo != null && sysAm.setCommunicationDevice(deviceInfo)) {
                logger.d("directRouteAudioDevice: setCommunicationDevice succeeded for type=$type")
                return
            }
            logger.w("directRouteAudioDevice: setCommunicationDevice failed for type=$type, falling back")
        }

        // setSpeakerphoneOn works in both MODE_IN_CALL and MODE_IN_COMMUNICATION.
        // It is deprecated since API 31 but remains the correct fallback when MIUI
        // misclassifies the outgoing self-managed call as cellular (MODE_IN_CALL).
        @Suppress("DEPRECATION")
        sysAm.isSpeakerphoneOn = (type == AudioDeviceType.SPEAKER)
        logger.d("directRouteAudioDevice: setSpeakerphoneOn=${type == AudioDeviceType.SPEAKER}")
    }

    /**
     * Locates the best matching endpoint for a requested speaker state.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun findSpeakerEndpoint(isActive: Boolean): CallEndpoint? {
        if (isActive) {
            return availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
        }

        // Fallback priority: Bluetooth -> Wired -> Streaming -> Earpiece
        return availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
            ?: availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET }
            ?: availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_STREAMING }
            ?: availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
    }

    /**
     * Logic to determine the correct audio route on legacy devices.
     */
    private fun determineLegacyRoute(isActive: Boolean): Int {
        if (isActive) return CallAudioState.ROUTE_SPEAKER

        // Fallback priority: Bluetooth -> Wired -> Earpiece
        return when {
            audioManager.isBluetoothConnected() -> CallAudioState.ROUTE_BLUETOOTH
            audioManager.isWiredHeadsetConnected() -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> CallAudioState.ROUTE_EARPIECE
        }
    }

    /**
     * Maps a [DisconnectCause] to the corresponding [CallLifecycleEvent] broadcast event.
     *
     * [DisconnectCause.REMOTE] maps to [CallLifecycleEvent.DeclineCall].
     * All other causes (local hang-up, rejection, timeout, etc.) map to [CallLifecycleEvent.HungUp].
     */
    private fun eventForDisconnectCause(cause: DisconnectCause?): CallLifecycleEvent =
        if (cause?.code == DisconnectCause.REMOTE) {
            CallLifecycleEvent.DeclineCall
        } else {
            CallLifecycleEvent.HungUp
        }

    /**
     * Terminates the connection with the given [disconnectCause].
     *
     * Uses [state] as the guard: if the connection is not yet [STATE_DISCONNECTED], runs
     * the full cleanup via [setDisconnected] and [onDisconnect]. If already disconnected,
     * skips cleanup and re-dispatches the broadcast using the stored [disconnectCause] so
     * late-arriving consumers (e.g. a one-shot endCall confirmation receiver) still receive
     * teardown confirmation without waiting for a timeout.
     *
     * All broadcast consumers are expected to be idempotent.
     *
     * @param disconnectCause The reason for disconnection.
     */
    fun terminateWithCause(disconnectCause: DisconnectCause) {
        if (state != STATE_DISCONNECTED) {
            setDisconnected(disconnectCause)
            onDisconnect()
        } else {
            logger.v("terminateWithCause: already disconnected for callId: $callId, re-dispatching stored cause")
            dispatcher(eventForDisconnectCause(this.disconnectCause), metadata)
        }
    }

    /**
     * Implementation of [OutcomeReceiver] for monitoring endpoint switches.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class EndpointChangeReceiver(
        private val endpoint: CallEndpoint,
    ) : OutcomeReceiver<Void, CallEndpointException> {
        override fun onResult(p0: Void?) {
            logger.d("Endpoint successfully changed to: $endpoint")
            synchronized(this@PhoneConnection) {
                if (pendingEndpointRequest?.identifier == endpoint.identifier) {
                    pendingEndpointRequest = null
                }
            }
        }

        override fun onError(error: CallEndpointException) {
            logger.e("Endpoint change failed for $endpoint: ${error.message}")
            synchronized(this@PhoneConnection) {
                if (pendingEndpointRequest?.identifier == endpoint.identifier) {
                    pendingEndpointRequest = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhoneConnection"
        private val logger = Log(TAG)

        /**
         * Shared single-thread executor for handling endpoint changes efficiently across all connections.
         * Using a shared executor prevents resource exhaustion and ensures sequential execution.
         */
        private val audioEndpointChangeExecutor = Executors.newSingleThreadExecutor()

        /**
         * Factory method for incoming call instances.
         */
        internal fun createIncomingPhoneConnection(
            context: Context,
            dispatcher: PerformDispatchHandle,
            metadata: CallMetadata,
            onDisconnect: (connection: PhoneConnection) -> Unit,
        ) = PhoneConnection(
            context = context,
            dispatcher = dispatcher,
            metadata = metadata,
            onDisconnectCallback = onDisconnect,
            timeout = ConnectionTimeout.createIncomingConnectionTimeout(context),
        ).apply {
            setInitialized()
            setRinging()
        }

        /**
         * Factory method for outgoing call instances.
         */
        internal fun createOutgoingPhoneConnection(
            context: Context,
            dispatcher: PerformDispatchHandle,
            metadata: CallMetadata,
            onDisconnect: (connection: PhoneConnection) -> Unit,
        ) = PhoneConnection(
            context = context,
            dispatcher = dispatcher,
            metadata = metadata,
            onDisconnectCallback = onDisconnect,
            timeout = ConnectionTimeout.createOutgoingConnectionTimeout(context),
        ).apply {
            setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)
            setInitialized()
            setDialing()
        }
    }
}

/**
 * Handles automated disconnection for calls that remain in transient states too long.
 */
class ConnectionTimeout(
    val timeoutDurationMs: Long,
    val states: List<Int>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Starts the countdown timer for the current state.
     */
    fun start(timeoutCallback: () -> Unit) {
        cancel()
        timeoutRunnable = Runnable(timeoutCallback)
        handler.postDelayed(timeoutRunnable!!, timeoutDurationMs)
    }

    /**
     * Stops and clears any pending timeout timers.
     */
    fun cancel() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
    }

    companion object {
        /**
         * Telecom states that trigger the timeout for incoming calls.
         */
        private val DEFAULT_INCOMING_STATES = listOf(Connection.STATE_NEW, Connection.STATE_RINGING)

        /**
         * Telecom states that trigger the timeout for outgoing calls.
         */
        private val DEFAULT_OUTGOING_STATES = listOf(Connection.STATE_DIALING)

        /**
         * Creates a timeout configuration for outgoing dialing.
         * The duration is read from [StorageDelegate.Timeout] so it can be configured via [CallkeepAndroidOptions].
         */
        fun createOutgoingConnectionTimeout(context: Context) = ConnectionTimeout(StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context), DEFAULT_OUTGOING_STATES)

        /**
         * Creates a timeout configuration for incoming ringing.
         * The duration is read from [StorageDelegate.Timeout] so it can be configured via [CallkeepAndroidOptions].
         */
        fun createIncomingConnectionTimeout(context: Context) = ConnectionTimeout(StorageDelegate.Timeout.getIncomingCallTimeoutMs(context), DEFAULT_INCOMING_STATES)
    }
}
