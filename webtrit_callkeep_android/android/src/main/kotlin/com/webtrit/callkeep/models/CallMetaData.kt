package com.webtrit.callkeep.models

import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst

data class CallMetadata(
    val callId: String,
    val displayName: String? = null,
    val handle: CallHandle? = null,
    val hasVideo: Boolean = false,
    val hasSpeaker: Boolean = false,
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
        ringtonePath?.let { putString(CALL_RINGTONE_PATH, it) }
        displayName?.let { putString(CallDataConst.DISPLAY_NAME, it) }
        handle?.let { putBundle(CallDataConst.NUMBER, it.toBundle()) }
        dualToneMultiFrequency?.let { putChar(CallDataConst.DTMF, it) }
        createdTime?.let { putLong(CALL_METADATA_CREATED_TIME, it) }
        acceptedTime?.let { putLong(CALL_METADATA_ACCEPTED_TIME, it) }
    }

    fun mergeWith(other: CallMetadata?): CallMetadata {
        return CallMetadata(
            callId = other?.callId ?: callId,
            displayName = other?.displayName ?: displayName,
            handle = other?.handle ?: handle,
            hasVideo = other?.hasVideo ?: hasVideo,
            hasSpeaker = other?.hasSpeaker ?: hasSpeaker,
            proximityEnabled = other?.proximityEnabled ?: proximityEnabled,
            hasMute = other?.hasMute ?: hasMute,
            hasHold = other?.hasHold ?: hasHold,
            dualToneMultiFrequency = other?.dualToneMultiFrequency ?: dualToneMultiFrequency,
            ringtonePath = other?.ringtonePath ?: ringtonePath,
            createdTime = other?.createdTime ?: createdTime,
            acceptedTime = other?.acceptedTime ?: acceptedTime
        )
    }

    override fun toString(): String {
        return "CallMetadata(callId=$callId, displayName=$displayName, handle=$handle, hasVideo=$hasVideo, hasSpeaker=$hasSpeaker, hasMute=$hasMute, hasHold=$hasHold, dualToneMultiFrequency=$dualToneMultiFrequency)"
    }

    companion object {
        private const val CALL_METADATA_CREATED_TIME = "CALL_METADATA_CREATED_TIME"
        private const val CALL_METADATA_ACCEPTED_TIME = "CALL_METADATA_ACCEPTED_TIME"
        private const val CALL_RINGTONE_PATH = "CALL_RINGTONE_PATH"

        fun fromBundle(bundle: Bundle): CallMetadata {
            val metadata = fromBundleOrNull(bundle)
            return metadata ?: throw IllegalArgumentException("Missing required callId property in Bundle")
        }

        fun fromBundleOrNull(bundle: Bundle): CallMetadata? {
            val callId = bundle.getString(CallDataConst.CALL_ID) ?: return null

            return CallMetadata(
                callId = callId,
                displayName = bundle.getString(CallDataConst.DISPLAY_NAME),
                handle = bundle.getBundle(CallDataConst.NUMBER)?.let { CallHandle.fromBundle(it) },
                hasVideo = bundle.getBoolean(CallDataConst.HAS_VIDEO, false),
                hasSpeaker = bundle.getBoolean(CallDataConst.HAS_SPEAKER, false),
                proximityEnabled = bundle.getBoolean(CallDataConst.PROXIMITY_ENABLED, false),
                hasMute = bundle.getBoolean(CallDataConst.HAS_MUTE, false),
                hasHold = bundle.getBoolean(CallDataConst.HAS_HOLD, false),
                dualToneMultiFrequency = bundle.getChar(CallDataConst.DTMF),
                ringtonePath = bundle.getString(CALL_RINGTONE_PATH),
                createdTime = bundle.getLong(CALL_METADATA_CREATED_TIME),
                acceptedTime = bundle.getLong(CALL_METADATA_ACCEPTED_TIME)
            )
        }
    }
}
