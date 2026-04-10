package com.webtrit.callkeep.models

import android.os.Bundle

enum class SignalingStatus {
    DISCONNECTING,
    DISCONNECT,
    CONNECTING,
    CONNECT,
    FAILURE,
    ;

    companion object {
        const val KEY = "signalingStatus"

        fun fromBundle(bundle: Bundle?): SignalingStatus? =
            bundle?.getString(KEY)?.let {
                valueOf(it)
            }
    }

    fun toBundle(): Bundle = Bundle().apply { putString(KEY, name) }
}
