package com.webtrit.callkeep.models

import android.os.Bundle

enum class SignalingStatus {
    DISCONNECTING, DISCONNECT, CONNECTING, CONNECT, FAILURE;

    companion object {
        const val KEY = "signalingStatus"

        fun fromBundle(bundle: Bundle): SignalingStatus {
            return valueOf(bundle.getString(KEY) ?: error("Missing signalingStatus"))
        }
    }

    fun toBundle(): Bundle {
        return Bundle().apply { putString(KEY, name) }
    }
}

