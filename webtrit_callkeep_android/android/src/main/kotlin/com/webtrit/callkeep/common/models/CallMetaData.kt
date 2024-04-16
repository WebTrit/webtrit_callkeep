package com.webtrit.callkeep.common.models

import android.net.Uri
import android.os.Bundle

import com.webtrit.callkeep_android.generated.lib.src.consts.CallDataConst

data class CallMetadata(
    val callId: String,
    val displayName: String? = null,
    val handle: CallHandle? = null,
    val hasVideo: Boolean? = null,
    val hasSpeaker: Boolean? = null,
    val proximityEnabled: Boolean? = null,
    val hasMute: Boolean? = null,
    val hasHold: Boolean? = null,
    val dualToneMultiFrequency: String? = null,
    val paths: CallPaths? = null,
    val ringtonePath : String? = null,
    val createdTime: Long? = null,
    val acceptedTime: Long? = null,
) {
    val number get() = handle?.number ?: "Undefine"

    val isVideo get() = hasVideo ?: false

    val isSpeaker get() = hasSpeaker ?: false

    val isProximityEnabled get() = proximityEnabled ?: false

    val isHold get() = hasHold ?: false

    val isMute get() = hasMute ?: false

    val name get() = if (displayName.isNullOrEmpty()) number else displayName

    val dtmf get() = dualToneMultiFrequency?.getOrNull(0)

    private fun toQueries(): String {
        return bundleToQueries()
    }

    fun getCallUri(): Uri =
        if (paths?.callPath == null) Uri.parse("/") else Uri.parse("${paths.callPath}?${toQueries()}")

    fun getMainUri(): Uri = Uri.parse("${paths!!.mainPath}?${toQueries()}")

    fun toBundle(): Bundle {
        val bundle = Bundle()

        // Add callIdentifier to the bundle
        bundle.putString(CallDataConst.CALL_ID, this.callId)
        paths?.let { bundle.putBundle("paths", it.toBundle()) }
        ringtonePath?.let { bundle.putString("ringtonePath", it) }

        // Add other properties to the bundle if they are not null
        displayName?.let { bundle.putString(CallDataConst.DISPLAY_NAME, it) }
        handle?.let { bundle.putBundle(CallDataConst.NUMBER, it.toBundle()) }
        hasVideo?.let { bundle.putBoolean(CallDataConst.HAS_VIDEO, it) }
        hasSpeaker?.let { bundle.putBoolean(CallDataConst.HAS_SPEAKER, it) }
        proximityEnabled?.let { bundle.putBoolean(CallDataConst.PROXIMITY_ENABLED, it) }
        hasMute?.let { bundle.putBoolean(CallDataConst.HAS_MUTE, it) }
        hasHold?.let { bundle.putBoolean(CallDataConst.HAS_HOLD, it) }
        dualToneMultiFrequency?.let { bundle.putString(CallDataConst.DTMF, it) }
        createdTime?.let { bundle.putLong(CALL_METADATA_CREATED_TIME, it) }
        acceptedTime?.let { bundle.putLong(CALL_METADATA_ACCEPTED_TIME, it) }

        return bundle
    }

    private fun bundleToQueries(): String {
        val parameters = mapOf<String, Any?>(
            CallDataConst.CALL_ID to this.callId,
            CallDataConst.DISPLAY_NAME to displayName,
            CallDataConst.NUMBER to handle?.number,
            // Add the other properties you want to extract from the `Bundle`
            CallDataConst.HAS_VIDEO to isVideo,
            CallDataConst.HAS_SPEAKER to hasSpeaker,
            CallDataConst.PROXIMITY_ENABLED to proximityEnabled,
            CallDataConst.HAS_MUTE to hasMute,
            CallDataConst.HAS_HOLD to isHold,
            CallDataConst.DTMF to dtmf
        )

        val queryList = mutableListOf<String>()
        parameters.forEach { (key, value) ->
            value?.let { queryList.add("$key=$value") }
        }

        return queryList.joinToString(separator = "&")
    }

    override fun toString(): String {
        return "CallMetaData(callIdentifier=${this.callId}, displayName=$displayName, handle=$handle, hasVideo=$hasVideo, hasSpeaker=$hasSpeaker, hasMute=$hasMute, hasHold=$hasHold, dualToneMultiFrequency=$dualToneMultiFrequency, paths=$paths)"
    }

    companion object {
        private const val CALL_METADATA_CREATED_TIME = "CALL_METADATA_CREATED_TIME"
        private const val CALL_METADATA_ACCEPTED_TIME = " CALL_METADATA_ACCEPTED_TIME"


        fun fromBundle(bundle: Bundle): CallMetadata {
            val callIdentifier = bundle.getString(CallDataConst.CALL_ID)
                ?: throw IllegalArgumentException("Missing required callIdentifier property in Bundle")

            val displayName = bundle.getString(CallDataConst.DISPLAY_NAME)
            val handle = bundle.getBundle(CallDataConst.NUMBER)?.let {
                CallHandle.fromBundle(it)
            }
            val paths = bundle.getBundle("paths")?.let {
                CallPaths.fromBundle(it)
            }
            val ringtonePath = bundle.getString("ringtonePath")

            val hasVideo = bundle.getBoolean(CallDataConst.HAS_VIDEO, false)
            val hasSpeaker = bundle.getBoolean(CallDataConst.HAS_SPEAKER, false)
            val proximityEnabled = bundle.getBoolean(CallDataConst.PROXIMITY_ENABLED, false)
            val hasMute = bundle.getBoolean(CallDataConst.HAS_MUTE, false)
            val hasHold = bundle.getBoolean(CallDataConst.HAS_HOLD, false)
            val dualToneMultiFrequency = bundle.getString(CallDataConst.DTMF)

            val createdTime = bundle.getLong(CALL_METADATA_CREATED_TIME)
            val acceptedTime = bundle.getLong(CALL_METADATA_ACCEPTED_TIME)

            return CallMetadata(
                callIdentifier,
                displayName,
                handle,
                hasVideo,
                hasSpeaker,
                proximityEnabled,
                hasMute,
                hasHold,
                dualToneMultiFrequency,
                paths,
                ringtonePath,
                createdTime,
                acceptedTime
            )
        }
    }
}
