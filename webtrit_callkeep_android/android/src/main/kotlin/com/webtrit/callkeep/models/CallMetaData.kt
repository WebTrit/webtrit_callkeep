package com.webtrit.callkeep.models

import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.extensions.getLongOrNull
import com.webtrit.callkeep.common.parcelableArrayList

/**
 * WARNING: DO NOT USE @Parcelize OR Parcelable FOR THIS CLASS.
 *
 * Technical Reason:
 * Objects of this class are passed to the Android system process via the Telecom Framework (ConnectionService).
 * The system process (system_server) often attempts to unmarshal the Bundle contents for internal
 * operations, such as logging (ConnectionRequest.toString()) or inspecting transaction extras.
 *
 * Since [CallMetadata] is a custom class defined within the application, the system's ClassLoader
 * is unaware of it. If a Parcelable object of this type is included in the Bundle, the system
 * will throw an [android.os.BadParcelableException] caused by a [java.lang.ClassNotFoundException].
 * This results in a fatal crash of the system process or the connection service.
 *
 * Solution:
 * Use manual serialization by packing individual fields into a [Bundle] using standard Android
 * types (String, Boolean, Long, etc.). These types can be safely unmarshalled by the system
 * without loading application-specific classes.
 */
data class CallMetadata(
    val callId: String,
    val displayName: String? = null,
    val handle: CallHandle? = null,
    val hasVideo: Boolean = false,
    val hasSpeaker: Boolean = false,
    val audioDevice: AudioDevice? = null,
    val audioDevices: List<AudioDevice> = emptyList(),
    val proximityEnabled: Boolean = false,
    val hasMute: Boolean = false,
    val hasHold: Boolean = false,
    val dualToneMultiFrequency: Char? = null,
    val ringtonePath: String? = null,
    val createdTime: Long? = null,
    val acceptedTime: Long? = null,
) {
    val number: String get() = handle?.number ?: "Undefined"
    val name: String get() = displayName?.takeIf { it.isNotEmpty() } ?: number

    fun toBundle(): Bundle = Bundle().apply {
        putString(CallDataConst.CALL_ID, callId)
        putBoolean(CallDataConst.HAS_VIDEO, hasVideo)
        putBoolean(CallDataConst.HAS_SPEAKER, hasSpeaker)
        putBoolean(CallDataConst.PROXIMITY_ENABLED, proximityEnabled)
        putBoolean(CallDataConst.HAS_MUTE, hasMute)
        putBoolean(CallDataConst.HAS_HOLD, hasHold)

        audioDevice?.let { putBundle(CallDataConst.AUDIO_DEVICE, it.toBundle()) }
        // Mandatory use of ArrayList<Bundle> to prevent ClassNotFoundException in the system process when unmarshalling Telecom extras.
        val deviceBundles = ArrayList(audioDevices.map { it.toBundle() })
        putParcelableArrayList(CallDataConst.AUDIO_DEVICES, deviceBundles)

        ringtonePath?.let { putString(CALL_RINGTONE_PATH, it) }
        displayName?.let { putString(CallDataConst.DISPLAY_NAME, it) }
        handle?.let { putBundle(CallDataConst.NUMBER, it.toBundle()) }

        dualToneMultiFrequency?.let { putChar(CallDataConst.DTMF, it) }
        createdTime?.let { putLong(CALL_METADATA_CREATED_TIME, it) }
        acceptedTime?.let { putLong(CALL_METADATA_ACCEPTED_TIME, it) }
    }

    fun mergeWith(other: CallMetadata?): CallMetadata {
        if (other == null) return this

        return copy(
            callId = other.callId,
            displayName = other.displayName ?: displayName,
            handle = other.handle ?: handle,
            hasVideo = other.hasVideo,
            hasSpeaker = other.hasSpeaker,
            audioDevice = other.audioDevice ?: audioDevice,
            // logic: only replace if new list is not empty.
            // Warning: prevents clearing the list via merge.
            audioDevices = other.audioDevices.ifEmpty { audioDevices },
            proximityEnabled = other.proximityEnabled,
            hasMute = other.hasMute,
            hasHold = other.hasHold,
            dualToneMultiFrequency = other.dualToneMultiFrequency ?: dualToneMultiFrequency,
            ringtonePath = other.ringtonePath ?: ringtonePath,
            createdTime = other.createdTime ?: createdTime,
            acceptedTime = other.acceptedTime ?: acceptedTime
        )
    }

    override fun toString(): String {
        return "CallMetadata(" + "callId='$callId', " + "displayName=${displayName?.let { "'$it'" }}, " + "handle=$handle, " + "hasVideo=$hasVideo, " + "hasSpeaker=$hasSpeaker, " + "audioDevice=$audioDevice, " + "audioDevices=$audioDevices, " + "proximityEnabled=$proximityEnabled, " + "hasMute=$hasMute, " + "hasHold=$hasHold, " + "dualToneMultiFrequency=$dualToneMultiFrequency, " + "ringtonePath=${ringtonePath?.let { "'$it'" }}, " + "createdTime=$createdTime, " + "acceptedTime=$acceptedTime" + ")"
    }

    companion object {
        private const val CALL_METADATA_CREATED_TIME = "CALL_METADATA_CREATED_TIME"
        private const val CALL_METADATA_ACCEPTED_TIME = "CALL_METADATA_ACCEPTED_TIME"
        private const val CALL_RINGTONE_PATH = "CALL_RINGTONE_PATH"

        private const val DEFAULT_CHAR_VALUE = '\u0000'

        fun fromBundle(bundle: Bundle): CallMetadata = fromBundleOrNull(bundle)
            ?: throw IllegalArgumentException("Missing required callId property in Bundle")

        fun fromBundleOrNull(bundle: Bundle): CallMetadata? {
            val callId = bundle.getString(CallDataConst.CALL_ID) ?: return null

            return CallMetadata(
                callId = callId,
                displayName = bundle.getString(CallDataConst.DISPLAY_NAME),
                handle = bundle.getBundle(CallDataConst.NUMBER)?.let { CallHandle.fromBundle(it) },
                hasVideo = bundle.getBoolean(CallDataConst.HAS_VIDEO, false),
                hasSpeaker = bundle.getBoolean(CallDataConst.HAS_SPEAKER, false),
                audioDevice = bundle.getBundle(CallDataConst.AUDIO_DEVICE)
                    ?.let { AudioDevice.fromBundle(it) },
                audioDevices = bundle.extractAudioDevices(),
                proximityEnabled = bundle.getBoolean(CallDataConst.PROXIMITY_ENABLED, false),
                hasMute = bundle.getBoolean(CallDataConst.HAS_MUTE, false),
                hasHold = bundle.getBoolean(CallDataConst.HAS_HOLD, false),
                dualToneMultiFrequency = bundle.getChar(CallDataConst.DTMF)
                    .takeIf { it != DEFAULT_CHAR_VALUE },
                ringtonePath = bundle.getString(CALL_RINGTONE_PATH),
                createdTime = bundle.getLongOrNull(CALL_METADATA_CREATED_TIME),
                acceptedTime = bundle.getLongOrNull(CALL_METADATA_ACCEPTED_TIME)
            )
        }

        private fun Bundle.extractAudioDevices(): List<AudioDevice> {
            val list = parcelableArrayList<Bundle>(CallDataConst.AUDIO_DEVICES)
            return list?.mapNotNull { AudioDevice.fromBundle(it) } ?: emptyList()
        }
    }
}
