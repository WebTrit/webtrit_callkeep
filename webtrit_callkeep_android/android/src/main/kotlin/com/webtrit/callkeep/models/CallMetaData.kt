package com.webtrit.callkeep.models

import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.extensions.getBooleanOrNull
import com.webtrit.callkeep.common.extensions.getCharOrNull
import com.webtrit.callkeep.common.extensions.getLongOrNull
import com.webtrit.callkeep.common.extensions.getStringOrNull
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
 *
 * @property speakerOnVideo Controls whether the speakerphone should be automatically enabled when a video call
 * is established or upgraded. If null, the system defaults to true (enabled).
 */
data class CallMetadata(
    val callId: String,
    val displayName: String? = null,
    val handle: CallHandle? = null,
    val hasVideo: Boolean? = null,
    val speakerOnVideo: Boolean? = null,
    val hasSpeaker: Boolean? = null,
    val audioDevice: AudioDevice? = null,
    val audioDevices: List<AudioDevice> = emptyList(),
    val proximityEnabled: Boolean? = null,
    val hasMute: Boolean? = null,
    val hasHold: Boolean? = null,
    val dualToneMultiFrequency: Char? = null,
    val ringtonePath: String? = null,
    val createdTime: Long? = null,
    val acceptedTime: Long? = null,
) {
    val number: String get() = handle?.number ?: "Undefined"
    val name: String get() = displayName?.takeIf { it.isNotEmpty() } ?: number

    fun toBundle(): Bundle =
        Bundle().apply {
            putString(CallDataConst.CALL_ID, callId)
            hasVideo?.let { putBoolean(CallDataConst.HAS_VIDEO, it) }
            hasSpeaker?.let { putBoolean(CallDataConst.HAS_SPEAKER, it) }
            speakerOnVideo?.let { putBoolean(CALL_METADATA_EXTRA_SPEAKER_ON_VIDEO, it) }
            proximityEnabled?.let { putBoolean(CallDataConst.PROXIMITY_ENABLED, it) }
            hasMute?.let { putBoolean(CallDataConst.HAS_MUTE, it) }
            hasHold?.let { putBoolean(CallDataConst.HAS_HOLD, it) }

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

    /**
     * Updates the current metadata with values from [other].
     *
     * **Merge Strategy:**
     * - **Nullable Fields (including Booleans):** Updated only if [other] provides a non-null value. Existing values are preserved if [other] has nulls.
     * - **Collections (audioDevices):** Updated only if [other]'s list is **non-empty**.
     *
     * **Limitations:**
     * - **Audio Devices:** It is **not possible to clear** the [audioDevices] list using this method. Passing an empty list in [other] will cause the existing list to be preserved.
     * - **Unsetting Values:** It is not possible to unset nullable fields (set them back to null) via this method.
     *
     * @param other The metadata object containing newer values.
     * @return A new [CallMetadata] instance with merged values, or `this` if [other] is null.
     */
    fun mergeWith(other: CallMetadata?): CallMetadata {
        if (other == null) return this

        return copy(
            callId = other.callId,
            displayName = other.displayName ?: displayName,
            handle = other.handle ?: handle,
            hasVideo = other.hasVideo ?: hasVideo,
            hasSpeaker = other.hasSpeaker ?: hasSpeaker,
            speakerOnVideo = other.speakerOnVideo ?: speakerOnVideo,
            audioDevice = other.audioDevice ?: audioDevice,
            audioDevices = other.audioDevices.ifEmpty { audioDevices },
            proximityEnabled = other.proximityEnabled ?: proximityEnabled,
            hasMute = other.hasMute ?: hasMute,
            hasHold = other.hasHold ?: hasHold,
            dualToneMultiFrequency = other.dualToneMultiFrequency ?: dualToneMultiFrequency,
            ringtonePath = other.ringtonePath ?: ringtonePath,
            createdTime = other.createdTime ?: createdTime,
            acceptedTime = other.acceptedTime ?: acceptedTime,
        )
    }

    companion object {
        private const val CALL_METADATA_CREATED_TIME = "CALL_METADATA_CREATED_TIME"
        private const val CALL_METADATA_ACCEPTED_TIME = "CALL_METADATA_ACCEPTED_TIME"
        private const val CALL_RINGTONE_PATH = "CALL_RINGTONE_PATH"
        private const val CALL_METADATA_EXTRA_SPEAKER_ON_VIDEO = "EXTRA_SPEAKER_ON_VIDEO"
        private const val DEFAULT_CHAR_VALUE = '\u0000'

        fun fromBundle(bundle: Bundle): CallMetadata =
            fromBundleOrNull(bundle)
                ?: throw IllegalArgumentException("Missing required callId property in Bundle")

        fun fromBundleOrNull(bundle: Bundle): CallMetadata? {
            val callId = bundle.getString(CallDataConst.CALL_ID) ?: return null

            return CallMetadata(
                callId = callId,
                displayName = bundle.getStringOrNull(CallDataConst.DISPLAY_NAME),
                handle = bundle.getBundle(CallDataConst.NUMBER)?.let { CallHandle.fromBundle(it) },
                hasVideo = bundle.getBooleanOrNull(CallDataConst.HAS_VIDEO),
                hasSpeaker = bundle.getBooleanOrNull(CallDataConst.HAS_SPEAKER),
                speakerOnVideo = bundle.getBooleanOrNull(CALL_METADATA_EXTRA_SPEAKER_ON_VIDEO),
                audioDevice =
                    bundle
                        .getBundle(CallDataConst.AUDIO_DEVICE)
                        ?.let { AudioDevice.fromBundle(it) },
                audioDevices = bundle.extractAudioDevices(),
                proximityEnabled = bundle.getBooleanOrNull(CallDataConst.PROXIMITY_ENABLED),
                hasMute = bundle.getBooleanOrNull(CallDataConst.HAS_MUTE),
                hasHold = bundle.getBooleanOrNull(CallDataConst.HAS_HOLD),
                dualToneMultiFrequency =
                    bundle
                        .getCharOrNull(CallDataConst.DTMF)
                        .takeIf { it != DEFAULT_CHAR_VALUE },
                ringtonePath = bundle.getStringOrNull(CALL_RINGTONE_PATH),
                createdTime = bundle.getLongOrNull(CALL_METADATA_CREATED_TIME),
                acceptedTime = bundle.getLongOrNull(CALL_METADATA_ACCEPTED_TIME),
            )
        }

        private fun Bundle.extractAudioDevices(): List<AudioDevice> {
            val list = parcelableArrayList<Bundle>(CallDataConst.AUDIO_DEVICES)
            return list?.mapNotNull { AudioDevice.fromBundle(it) } ?: emptyList()
        }
    }
}
