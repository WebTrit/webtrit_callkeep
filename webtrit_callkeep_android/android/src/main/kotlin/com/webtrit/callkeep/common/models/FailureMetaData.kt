package com.webtrit.callkeep.common.models

import android.os.Bundle

enum class OutgoingFailureType {
    UNENTITLED, EMERGENCY_NUMBER
}

open class FailureMetadata(
    private val message: String?,
    val outgoingFailureType: OutgoingFailureType = OutgoingFailureType.UNENTITLED,
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        message?.let {
            bundle.putString(FAILURE_METADATA_MESSAGE, it)
        }
        outgoingFailureType.let {
            bundle.putInt(FAILURE_OUTGOING_TYPE, it.ordinal)
        }
        return bundle
    }

    fun getThrowable(): Throwable {
        return Throwable(message ?: "Something happened")
    }


    companion object {
        private const val FAILURE_METADATA_MESSAGE = "FAILURE_METADATA_MESSAGE"
        private const val FAILURE_OUTGOING_TYPE = "FAILURE_OUTGOING_TYPE"

        fun fromBundle(bundle: Bundle): FailureMetadata {
            val message = bundle.getString(FAILURE_METADATA_MESSAGE)
            val rawOutgoingFailureType = bundle.getInt(FAILURE_OUTGOING_TYPE, 0)
            val outgoingFailureType = OutgoingFailureType.values()[rawOutgoingFailureType]
            return FailureMetadata(message, outgoingFailureType)
        }
    }
}
