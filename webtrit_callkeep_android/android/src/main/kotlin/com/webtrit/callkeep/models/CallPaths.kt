package com.webtrit.callkeep.models

import android.os.Bundle

import com.webtrit.callkeep_android.generated.lib.src.consts.CallPathKeyConst

data class CallPaths(
    val callPath: String,
    val mainPath: String,
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(CallPathKeyConst.CALL_PATH, callPath)
        bundle.putString(CallPathKeyConst.MAIN_PATH, mainPath)

        return bundle
    }

    companion object {
        fun fromBundle(bundle: Bundle): CallPaths {
            val callPath = bundle.getString(CallPathKeyConst.CALL_PATH)
            val mainPath = bundle.getString(CallPathKeyConst.CALL_PATH)

            if (callPath != null && mainPath != null) {
                return CallPaths(callPath, mainPath)
            } else {
                throw IllegalArgumentException("Missing required properties in CallPaths Bundle")
            }
        }
    }
}
