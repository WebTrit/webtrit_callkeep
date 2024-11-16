package com.webtrit.callkeep.models

import android.os.Bundle

data class CallHandle(
    val number: String,
) {
    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("number", number)
        return bundle
    }

    companion object {
        fun fromBundle(bundle: Bundle?): CallHandle {
            val number = bundle?.getString("number") ?: "undefined"
            return CallHandle(number)
        }
    }
}
