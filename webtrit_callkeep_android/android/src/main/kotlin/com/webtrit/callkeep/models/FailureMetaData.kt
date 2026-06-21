package com.webtrit.callkeep.models

import android.os.Bundle

enum class OutgoingFailureType {
    UNENTITLED,
    EMERGENCY_NUMBER,
}

open class FailureMetadata(
    val callMetadata: CallMetadata?,
    val message: String?,
    val outgoingFailureType: OutgoingFailureType = OutgoingFailureType.UNENTITLED,
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            // Serialize optional message
            message?.let { putString(FAILURE_METADATA_MESSAGE, it) }

            // Serialize optional call metadata as nested bundle
            callMetadata?.let { putBundle(FAILURE_CALL_METADATA, it.toBundle()) }

            putString(FAILURE_OUTGOING_TYPE, outgoingFailureType.name)
        }

    fun getThrowable(): Throwable = Throwable(message ?: "Something happened")

    companion object {
        private const val FAILURE_METADATA_MESSAGE = "FAILURE_METADATA_MESSAGE"
        private const val FAILURE_OUTGOING_TYPE = "FAILURE_OUTGOING_TYPE"
        private const val FAILURE_CALL_METADATA = "FAILURE_CALL_METADATA"

        fun fromBundle(bundle: Bundle): FailureMetadata {
            val callMetadataBundle = bundle.getBundle(FAILURE_CALL_METADATA)
            val callMetadata = callMetadataBundle?.let { CallMetadata.fromBundleOrNull(it) }

            val message = bundle.getString(FAILURE_METADATA_MESSAGE)
            val rawOutgoingFailureType = bundle.getString(FAILURE_OUTGOING_TYPE)
            val outgoingFailureType = OutgoingFailureType.entries
                .firstOrNull { it.name == rawOutgoingFailureType }
                ?: OutgoingFailureType.UNENTITLED
            return FailureMetadata(
                callMetadata = callMetadata,
                message = message,
                outgoingFailureType = outgoingFailureType,
            )
        }
    }
}
