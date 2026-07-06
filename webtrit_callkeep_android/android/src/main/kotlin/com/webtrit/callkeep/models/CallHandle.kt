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
        /**
         * Builds a [CallHandle] from a bundle, or returns `null` when the number is absent.
         *
         * A missing number must propagate upward as an absence so callers can decide the
         * fallback display value. It must never become a literal placeholder string, which
         * would otherwise be shown by the Telecom UI as the caller's phone number and would
         * break contact lookup.
         */
        fun fromBundle(bundle: Bundle?): CallHandle? {
            val number = bundle?.getString("number") ?: return null
            return CallHandle(number)
        }
    }
}
