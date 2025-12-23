package com.webtrit.callkeep.services.services.connection

import java.util.concurrent.Executors

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
import com.webtrit.callkeep.managers.AudioManager
import com.webtrit.callkeep.managers.NotificationManager
import com.webtrit.callkeep.models.AudioDevice
import com.webtrit.callkeep.models.AudioDeviceType
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle

/**
 * Manages an individual phone connection, handling state transitions and audio routing.
 */
class PhoneConnection internal constructor(
    private val context: Context,
    private val dispatcher: PerformDispatchHandle,
    var metadata: CallMetadata,
    var onDisconnectCallback: (connection: PhoneConnection) -> Unit,
    var timeout: ConnectionTimeout? = null,
) : Connection() {
    private var isMute = false
    private var isHasSpeaker = false
    private var answer = false
    private var disconnected = false

    private var availableCallEndpoints: List<CallEndpoint> = emptyList()

    private val notificationManager = NotificationManager()
    private val audioManager = AudioManager(context)

    init {
        audioModeIsVoip = true
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD

        setInitializing()
        updateData(metadata)
    }

    /**
     * Unique identifier for the call session.
     */
    val id: String
        get() = metadata.callId

    /**
     * Indicates whether the user has performed the answer action.
     */
    fun isAnswered(): Boolean = answer

    /**
     * Transitions the connection to an active state and ensures the UI is visible.
     */
    fun establish() {
        logger.d("Establishing connection for callId: $id")
        context.startActivity(Platform.getLaunchActivity(context))
        setActive()
    }

    /**
     * Synchronizes the internal mute state and notifies the application.
     */
    fun changeMuteState(isMute: Boolean) {
        logger.d("Changing mute state to: $isMute for callId: $id")
        this.isMute = isMute
        dispatcher(ConnectionPerform.AudioMuting, metadata.copy(hasMute = this.isMute))
    }

    /**
     * Invoked by the system when the incoming call interface should be displayed.
     */
    override fun onShowIncomingCallUi() {
        logger.d("Showing incoming call UI for callId: $id")
        notificationManager.showIncomingCallNotification(metadata)
        audioManager.startRingtone(metadata.ringtonePath)
    }

    /**
     * Handles the transition when a user accepts an incoming call.
     */
    override fun onAnswer() {
        logger.i("Answering call: $metadata")
        super.onAnswer()
        answer = true
        setActive()
        dispatcher(ConnectionPerform.AnswerCall, metadata)
        ActivityHolder.start(context)
    }

    /**
     * Handles the transition when a user rejects an incoming call.
     */
    override fun onReject() {
        logger.i("Rejecting call: $id")
        super.onReject()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    }

    /**
     * Performs final cleanup when the connection is terminated.
     */
    override fun onDisconnect() {
        logger.i("Disconnecting call: $id")
        super.onDisconnect()

        timeout?.cancel()
        notificationManager.cancelIncomingNotification(isAnswered())
        notificationManager.cancelActiveCallNotification(id)
        audioManager.stopRingtone()

        val event = if (disconnectCause?.code == DisconnectCause.REMOTE) {
            ConnectionPerform.DeclineCall
        } else {
            ConnectionPerform.HungUp
        }
        dispatcher(event, metadata)
        onDisconnectCallback.invoke(this)
        destroy()
    }

    /**
     * Updates the internal state to reflect a held call.
     */
    override fun onHold() {
        logger.d("Putting call on hold: $id")
        super.onHold()
        setOnHold()
        dispatcher(ConnectionPerform.ConnectionHolding, metadata.copy(hasHold = true))
    }

    /**
     * Resumes the call from a held state.
     */
    override fun onUnhold() {
        logger.d("Taking call off hold: $id")
        super.onUnhold()
        setActive()
        dispatcher(ConnectionPerform.ConnectionHolding, metadata.copy(hasHold = false))
    }

    /**
     * Dispatches a DTMF tone event to the application.
     */
    override fun onPlayDtmfTone(c: Char) {
        logger.d("Playing DTMF tone: $c for callId: $id")
        super.onPlayDtmfTone(c)
        dispatcher(ConnectionPerform.SentDTMF, metadata.copy(dualToneMultiFrequency = c))
    }

    /**
     * Orchestrates logical transitions based on the underlying Telecom state.
     */
    override fun onStateChanged(state: Int) {
        logger.v("Connection state changed to: $state for callId: $id")
        super.onStateChanged(state)
        handleConnectionTimeout(state)

        when (state) {
            STATE_DIALING -> onDialing()
            STATE_ACTIVE -> onActiveConnection()
        }
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
        logger.w("Timeout reached for callId: $id")
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

        state?.route?.let {
            isHasSpeaker = it == CallAudioState.ROUTE_SPEAKER
            dispatcher(
                ConnectionPerform.ConnectionHasSpeaker, metadata.copy(hasSpeaker = isHasSpeaker)
            )
        }

        val audioDevices = state?.supportedRouteMask?.let(::mapSupportedRoutes) ?: emptyList()
        dispatcher(ConnectionPerform.AudioDevicesUpdate, metadata.copy(audioDevices = audioDevices))

        val currentDevice =
            state?.route?.let(::mapRouteToAudioDevice) ?: AudioDevice(AudioDeviceType.UNKNOWN)
        dispatcher(ConnectionPerform.AudioDeviceSet, metadata.copy(audioDevice = currentDevice))
    }

    /**
     * Refreshes the audio state and endpoints for the application layer.
     */
    fun forceUpdateAudioState() {
        logger.d("Force updating audio state for callId: $id")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            updateModernAudioState()
        } else {
            callAudioState?.let(::onCallAudioStateChanged)
        }
    }

    /**
     * Triggers modern endpoint updates for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateModernAudioState() {
        onCallEndpointChanged(currentCallEndpoint)
        if (availableCallEndpoints.isNotEmpty()) {
            onAvailableCallEndpointsChanged(availableCallEndpoints)
        }
        dispatcher(ConnectionPerform.AudioMuting, metadata.copy(hasMute = isMute))
    }

    /**
     * Updates available audio destinations for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(callEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(callEndpoints)
        logger.d("Available call endpoints changed: $callEndpoints")
        availableCallEndpoints = callEndpoints
        val devices = callEndpoints.map(::mapEndpointToAudioDevice)
        dispatcher(ConnectionPerform.AudioDevicesUpdate, metadata.copy(audioDevices = devices))
    }

    /**
     * Updates the currently active audio destination for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        logger.i("Call endpoint changed to: $callEndpoint")
        val device = mapEndpointToAudioDevice(callEndpoint)
        dispatcher(ConnectionPerform.AudioDeviceSet, metadata.copy(audioDevice = device))

        isHasSpeaker = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER
        dispatcher(ConnectionPerform.ConnectionHasSpeaker, metadata.copy(hasSpeaker = isHasSpeaker))
    }

    /**
     * Syncs system mute state changes for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        logger.d("Mute state changed via system: $isMuted")
        isMute = isMuted
        dispatcher(ConnectionPerform.AudioMuting, metadata.copy(hasMute = isMute))
    }

    /**
     * Requests a change to a specific audio device.
     */
    fun setAudioDevice(device: AudioDevice) {
        logger.i("Setting audio device: $device for callId: $id")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint =
                availableCallEndpoints.firstOrNull { it.identifier == ParcelUuid.fromString(device.id!!) }
            if (endpoint != null) {
                performEndpointChange(endpoint)
            } else {
                logger.e("No suitable call endpoint found for the current audio state. Requested device: $device, callId: $id")
            }
        } else {
            setAudioRoute(mapDeviceTypeToRoute(device.type))
        }
    }

    /**
     * Legacy helper to toggle speakerphone state.
     */
    @Deprecated("Use setAudioDevice instead")
    fun changeSpeakerState(isActive: Boolean) {
        logger.d("Changing speaker state: $isActive for callId: $id")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            findSpeakerEndpoint(isActive)?.let(::performEndpointChange)
        } else {
            setAudioRoute(determineLegacyRoute(isActive))
        }
    }

    /**
     * Updates call identity and visual parameters.
     */
    fun updateData(metadata: CallMetadata) {
        this.metadata = this.metadata.mergeWith(metadata)
        extras = metadata.toBundle()
        setAddress(metadata.number.toUri(), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)
        applyVideoState(metadata.hasVideo)
    }

    /**
     * Terminates the connection from the local side before acceptance.
     */
    fun declineCall() {
        logger.d("Local decline for callId: $id")
        if (state == STATE_RINGING) {
            notificationManager.showMissedCallNotification(metadata)
            dispatcher(ConnectionPerform.MissedCall, metadata)
        }
        terminateWithCause(DisconnectCause(DisconnectCause.REMOTE))
    }

    /**
     * Terminates the active connection.
     */
    fun hungUp() {
        logger.d("Local hang up for callId: $id")
        dispatcher(ConnectionPerform.AudioMuting, metadata.copy(hasMute = isMute))
        terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))
    }

    /**
     * Logic triggered when the call enters an active talking state.
     */
    private fun onActiveConnection() {
        logger.i("Connection became active for callId: $id")
        audioManager.stopRingtone()
        notificationManager.cancelIncomingNotification(true)
        notificationManager.cancelMissedCall(metadata)
        notificationManager.showActiveCallNotification(id, metadata)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            val update = metadata.copy(hasMute = isMute, hasSpeaker = isHasSpeaker)
            dispatcher(ConnectionPerform.AudioMuting, update)
            dispatcher(ConnectionPerform.ConnectionHasSpeaker, update)
        }
    }

    /**
     * Logic triggered when the local side starts dialing.
     */
    private fun onDialing() {
        logger.i("Dialing callId: $id")
        dispatcher(ConnectionPerform.OngoingCall, metadata)
    }

    /**
     * Updates the video provider and profile based on session requirements.
     */
    private fun applyVideoState(hasVideo: Boolean) {
        if (hasVideo) {
            videoProvider = PhoneVideoProvider()
            videoState = VideoProfile.STATE_BIDIRECTIONAL
        } else {
            videoProvider = null
            videoState = VideoProfile.STATE_AUDIO_ONLY
        }
    }

    /**
     * Executes an asynchronous endpoint switch for API 34+.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun performEndpointChange(endpoint: CallEndpoint) {
        requestCallEndpointChange(
            endpoint, audioEndpointChangeExecutor, EndpointChangeReceiver(endpoint)
        )
    }

    /**
     * Converts a [CallEndpoint] into a domain [AudioDevice] model.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun mapEndpointToAudioDevice(endpoint: CallEndpoint) = AudioDevice(
        type = when (endpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> AudioDeviceType.EARPIECE
            CallEndpoint.TYPE_SPEAKER -> AudioDeviceType.SPEAKER
            CallEndpoint.TYPE_BLUETOOTH -> AudioDeviceType.BLUETOOTH
            CallEndpoint.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
            CallEndpoint.TYPE_STREAMING -> AudioDeviceType.STREAMING
            else -> AudioDeviceType.UNKNOWN
        }, name = endpoint.endpointName.toString(), id = endpoint.identifier.toString()
    )

    /**
     * Converts a [CallAudioState] route into a domain [AudioDevice] model.
     */
    private fun mapRouteToAudioDevice(route: Int) = AudioDevice(
        type = when (route) {
            CallAudioState.ROUTE_EARPIECE -> AudioDeviceType.EARPIECE
            CallAudioState.ROUTE_SPEAKER -> AudioDeviceType.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioDeviceType.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
            CallAudioState.ROUTE_STREAMING -> AudioDeviceType.STREAMING
            else -> AudioDeviceType.UNKNOWN
        }
    )

    /**
     * Parses the supported route mask into a list of [AudioDevice] models.
     */
    private fun mapSupportedRoutes(mask: Int) = listOfNotNull(
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) AudioDevice(AudioDeviceType.EARPIECE) else null,
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) AudioDevice(AudioDeviceType.SPEAKER) else null,
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) AudioDevice(AudioDeviceType.BLUETOOTH) else null,
        if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) AudioDevice(AudioDeviceType.WIRED_HEADSET) else null,
        if (mask and CallAudioState.ROUTE_STREAMING != 0) AudioDevice(AudioDeviceType.STREAMING) else null
    )

    /**
     * Maps domain device types back to Telecom route integers.
     */
    private fun mapDeviceTypeToRoute(type: AudioDeviceType) = when (type) {
        AudioDeviceType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        AudioDeviceType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
        AudioDeviceType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        AudioDeviceType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
    }

    /**
     * Locates the best matching endpoint for a requested speaker state.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun findSpeakerEndpoint(isActive: Boolean): CallEndpoint? {
        if (isActive) {
            return availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
        }
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
        return when {
            audioManager.isBluetoothConnected() -> CallAudioState.ROUTE_BLUETOOTH
            audioManager.isWiredHeadsetConnected() -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> CallAudioState.ROUTE_EARPIECE
        }
    }

    /**
     * Safely terminates the connection with a reason and prevents double-termination.
     */
    fun terminateWithCause(disconnectCause: DisconnectCause) {
        if (!disconnected) {
            disconnected = true
            setDisconnected(disconnectCause)
            onDisconnect()
        } else {
            logger.v("terminateWithCause: already disconnected for callId: $id")
        }
    }

    /**
     * Implementation of [OutcomeReceiver] for monitoring endpoint switches.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class EndpointChangeReceiver(private val endpoint: CallEndpoint) :
        OutcomeReceiver<Void, CallEndpointException> {
        override fun onResult(p0: Void?) = logger.d("Endpoint successfully changed to: $endpoint")
        override fun onError(error: CallEndpointException) =
            logger.e("Endpoint change failed for $endpoint: ${error.message}")
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
            timeout = ConnectionTimeout.createIncomingConnectionTimeout()
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
            timeout = ConnectionTimeout.createOutgoingConnectionTimeout()
        ).apply {
            setDialing()
            setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)
            if (!Build.MANUFACTURER.equals("Samsung", ignoreCase = true)) {
                setInitialized()
            }
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
         * Duration in milliseconds before a call in a transient state is automatically disconnected.
         */
        private const val TIMEOUT_DURATION_MS = 35000L

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
         */
        fun createOutgoingConnectionTimeout() =
            ConnectionTimeout(TIMEOUT_DURATION_MS, DEFAULT_OUTGOING_STATES)

        /**
         * Creates a timeout configuration for incoming ringing.
         */
        fun createIncomingConnectionTimeout() =
            ConnectionTimeout(TIMEOUT_DURATION_MS, DEFAULT_INCOMING_STATES)
    }
}
