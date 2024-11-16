package com.webtrit.callkeep.services.telecom.connection

import java.lang.Exception
import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log

import com.webtrit.callkeep.FlutterLog
import com.webtrit.callkeep.api.background.TelephonyBackgroundCallkeepApi
import com.webtrit.callkeep.api.foreground.TelephonyForegroundCallkeepApi
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.managers.AudioManager
import com.webtrit.callkeep.managers.NotificationManager

/**
 * Represents a phone connection for handling telephony calls.
 *
 * @param context The Android application context.
 * @param metadata The metadata associated with the call.
 */
class PhoneConnection internal constructor(
    private val context: Context,
    var metadata: CallMetadata,
) : Connection() {
    private var isMute = false
    private var isHasSpeaker = false
    private var answer = false

    private val notificationManager = NotificationManager(context)
    private val audioManager = AudioManager(context)

    init {
        audioModeIsVoip = true
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD

        setInitializing()
        updateData(metadata)
    }

    val id: String
        get() = this.metadata.callId

    /**
     * Checks if user press answer before.
     * @return true if user has been answered, false otherwise.
     */
    fun isAnswered(): Boolean {
        return answer
    }

    /**
     * Called when the caller begins communication.
     */
    fun establish() {
        Log.d(TAG, "PhoneConnection:establish")
        // Launch the activity if, for example, an outgoing call was started and an answer happened while the activity was hidden
        // This ensures that the user interface is properly displayed and active
        context.startActivity(Platform.getLaunchActivity(context))
        setActive()
    }

    /**
     * Change the mute state of the call.
     *
     * @param isMute True to mute the call, false to unmute it.
     */
    fun changeMuteState(isMute: Boolean) {
        this@PhoneConnection.audioManager.setMicrophoneMute(isMute)
        /**
         * There are known issues in Android 8.1 related to onCallAudioStateChanged, specifically,
         * it does not get triggered when we change the microphone mute state using audioManager.isMicrophoneMute.
         *
         *
         * To address this issue, we need trigger locally.
         */
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            //TODO: DO SINGLE ENDPOINT METHOD FOR SET STATE
            this.isMute = isMute
            TelephonyForegroundCallkeepApi.notifyMuting(
                context, metadata.copy(hasMute = this.isMute)
            )
        }
    }

    /**
     * Called when the incoming call UI should be displayed.
     */
    override fun onShowIncomingCallUi() {
        notificationManager.showIncomingCallNotification(metadata)
        this@PhoneConnection.audioManager.startRingtone(metadata.ringtonePath)
    }

    /**
     * Callback method invoked when an incoming call is answered.
     * This method is called when the user answers an incoming call.
     * Use this overload if the video state is not relevant.
     */
    override fun onAnswer() {
        super.onAnswer()
        answer = true
        FlutterLog.i(TAG, "onAnswer: $metadata")

        try {
            notificationManager.cancelIncomingNotification()
            notificationManager.cancelMissedCall(metadata)
            this@PhoneConnection.audioManager.stopRingtone()
        } catch (e: Exception) {
            FlutterLog.e(TAG, "onAnswer: $e")
        }

        TelephonyForegroundCallkeepApi.notifyAnswer(context, metadata)
        TelephonyBackgroundCallkeepApi.notifyAnswer(context, metadata)

        ActivityHolder.start(metadata, context)
    }

    /**
     * Called when the user rejects the incoming call.
     *
     * This method sets the call's disconnect cause to "Rejected" and initiates the call disconnect process.
     */
    override fun onReject() {
        super.onReject()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        onDisconnect()
    }

    /**
     * Called when the call is disconnected.
     *
     * This method stops the call ringtone, removes the call from the phone connection service,
     * cancels any active notifications, notifies the application about the declined call,
     * and performs cleanup tasks.
     */
    override fun onDisconnect() {
        super.onDisconnect()

        FlutterLog.i(TAG, "onDisconnect: ${metadata.callId}")

        PhoneConnectionService.remove(metadata.callId)
        notificationManager.cancelActiveNotification()
        this@PhoneConnection.audioManager.stopRingtone()
        TelephonyForegroundCallkeepApi.notifyDeclineCall(context, metadata)
        destroy()
    }

    /**
     * Called when the call is put on hold.
     *
     * This method updates the call's state to "On Hold" and notifies the application about the holding status change.
     */
    override fun onHold() {
        super.onHold()
        setOnHold()
        TelephonyForegroundCallkeepApi.notifyAboutHolding(context, metadata.copy(hasHold = true))
    }

    /**
     * Called when the call is taken off hold.
     *
     * This method sets the call back to the "Active" state and notifies the application about the holding status change.
     */
    override fun onUnhold() {
        super.onUnhold()
        setActive()
        TelephonyForegroundCallkeepApi.notifyAboutHolding(context, metadata.copy(hasHold = false))
    }

    /**
     * Called when a Dual-Tone Multi-Frequency (DTMF) tone is played during the call.
     *
     * This method notifies the application about the DTMF tone that was played.
     *
     * @param c The DTMF tone character that was played.
     */
    override fun onPlayDtmfTone(c: Char) {
        super.onPlayDtmfTone(c)
        TelephonyForegroundCallkeepApi.notifyAboutDTMF(
            context, metadata.copy(dualToneMultiFrequency = c)
        )
    }

    /**
     * Called when the state of the call changes.
     *
     * This method handles state changes and triggers specific actions based on the new call state.
     * It can be called when the call is dialing or when it becomes active.
     *
     * @param state The new state of the call.
     */
    override fun onStateChanged(state: Int) {
        super.onStateChanged(state)
        when (state) {
            STATE_DIALING -> onDialing()
            STATE_ACTIVE -> onActiveConnection()
        }
    }

    /**
     * Called when the audio state of the call changes.
     *
     * This method handles audio state changes, including muting and audio routing.
     * It addresses known issues in Android 8.1 related to onCallAudioStateChanged.
     * Depending on the Android version, it locally handles changes in microphone mute state
     * and audio route changes, and notifies the application about these changes.
     *
     * @param state The new audio state of the call.
     */
    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)

        // Check if the device is running Android version higher than O_MR1 (Android 8.1)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            state?.isMuted?.let {
                // Update the mute state locally
                this.isMute = it
                TelephonyForegroundCallkeepApi.notifyMuting(
                    context, metadata.copy(hasMute = this.isMute)
                )
            }
        }

        state?.route?.let {
            // Update the audio route state
            this.isHasSpeaker = it == CallAudioState.ROUTE_SPEAKER
            TelephonyForegroundCallkeepApi.notifyAudioRouteChanged(
                context, metadata.copy(hasSpeaker = this.isHasSpeaker)
            )
        }
    }

    /**
     * Update the call metadata with new data.
     *
     * This method updates the metadata associated with the call, including the caller's information,
     * number, and video state. It also sets the address and caller display name based on the metadata.
     *
     * @param metadata The updated call metadata.
     */
    fun updateData(metadata: CallMetadata) {
        this.metadata = this.metadata.mergeWith(metadata)
        this.extras = metadata.toBundle()
        setAddress(Uri.parse(metadata.number), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)
        changeVideoState(metadata.hasVideo)
    }

    /**
     * Change the speaker state of the call.
     *
     * @param isActive True if the speaker is active, false otherwise.
     */
    fun changeSpeakerState(isActive: Boolean) {
        var routeState = CallAudioState.ROUTE_EARPIECE

        if (isActive) {
            routeState = CallAudioState.ROUTE_SPEAKER
        } else if (this@PhoneConnection.audioManager.isBluetoothConnected()) {
            routeState = CallAudioState.ROUTE_BLUETOOTH
        } else if (this@PhoneConnection.audioManager.isWiredHeadsetConnected()) {
            routeState = CallAudioState.ROUTE_WIRED_HEADSET
        }
        setAudioRoute(routeState)
    }

    /**
     * Decline the call.
     */
    fun declineCall() {
        if (state == STATE_RINGING) {
            notificationManager.showMissedCallNotification(metadata)
            TelephonyBackgroundCallkeepApi.notifyMissedIncomingCall(context, metadata)
        }
        setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
        onDisconnect()
    }

    /**
     * Hang up the call.
     */
    fun hungUp() {
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        onDisconnect()
    }

    /**
     * Handle actions when the connection becomes active.
     */
    private fun onActiveConnection() {
        notificationManager.cancelActiveNotification()
        this@PhoneConnection.audioManager.stopRingtone()
        notificationManager.showActiveCallNotification(metadata)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            TelephonyForegroundCallkeepApi.notifyMuting(
                context, metadata.copy(hasMute = this.isMute)
            )
            TelephonyForegroundCallkeepApi.notifyAudioRouteChanged(
                context, metadata.copy(hasSpeaker = this.isHasSpeaker)
            )
        }
    }

    /**
     * Handle actions when the call is in the dialing state.
     */
    private fun onDialing() {
        TelephonyForegroundCallkeepApi.notifyOutgoingCall(context, metadata)
    }

    /**
     * Change the video state of the call.
     *
     * @param hasVideo True if the call has video, false otherwise.
     */
    private fun changeVideoState(hasVideo: Boolean) {
        if (hasVideo) {
            videoProvider = PhoneVideoProvider()
            videoState = VideoProfile.STATE_BIDIRECTIONAL
        } else {
            videoProvider = null
            videoState = VideoProfile.STATE_AUDIO_ONLY
        }
    }

    override fun toString(): String {
        return "PhoneConnection(metadata=$metadata, isMute=$isMute, isHasSpeaker=$isHasSpeaker, answer=$answer, id='$id')"
    }


    companion object {
        private const val TAG = "PhoneConnection"

        /**
         * Create an incoming phone connection.
         *
         * @param context The Android application context.
         * @param metadata The call metadata.
         * @return The created incoming phone connection.
         */
        fun createIncomingPhoneConnection(context: Context, metadata: CallMetadata) =
            PhoneConnection(context = context, metadata = metadata).apply {
                setInitialized()
                setRinging()
            }

        /**
         * Create an outgoing phone connection.
         *
         * @param context The Android application context.
         * @param metadata The call metadata.
         * @return The created outgoing phone connection.
         */
        fun createOutgoingPhoneConnection(context: Context, metadata: CallMetadata) =
            PhoneConnection(context = context, metadata = metadata).apply {
                setDialing()
                setCallerDisplayName(metadata.name, TelecomManager.PRESENTATION_ALLOWED)
                // ‍️Weirdly on some Samsung phones (A50, S9...) using `setInitialized` will not display the native UI ...
                // when making a call from the native Phone application. The call will still be displayed correctly without it.
                if (!Build.MANUFACTURER.equals("Samsung", ignoreCase = true)) {
                    setInitialized()
                }
            }
    }
}
