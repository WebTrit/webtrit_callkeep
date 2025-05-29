package com.webtrit.callkeep.models

import android.os.Bundle

enum class SignalingStatus {
    DISCONNECTING, DISCONNECT, CONNECTING, CONNECT, FAILURE;

    companion object {
        const val KEY = "signalingStatus"

        fun fromBundle(bundle: Bundle?): SignalingStatus? {
            return bundle?.getString(KEY)?.let {
                valueOf(it)
            }
        }
    }

    fun toBundle(): Bundle {
        return Bundle().apply { putString(KEY, name) }
    }
}

