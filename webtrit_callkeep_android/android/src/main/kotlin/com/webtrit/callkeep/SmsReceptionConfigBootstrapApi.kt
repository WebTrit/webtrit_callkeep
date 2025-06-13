package com.webtrit.callkeep

import android.content.Context
import android.util.Log
import com.webtrit.callkeep.common.StorageDelegate

class SmsReceptionConfigBootstrapApi(
    private val context: Context
) : PHostSmsReceptionConfigApi {
    override fun initializeSmsReception(
        messagePrefix: String,
        regexPattern: String,
        callback: (Result<Unit>) -> Unit
    ) {
        Log.i(TAG, "initializeSmsReception: prefix = $messagePrefix, regex = $regexPattern")
        try {
            Regex(regexPattern)

            StorageDelegate.IncomingCallSmsConfig.setSmsPrefix(context, messagePrefix)
            StorageDelegate.IncomingCallSmsConfig.setRegexPattern(context, regexPattern)

            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Invalid regex pattern: ${e.message}")
            callback(Result.failure(e))
        }
    }

    companion object {
        const val TAG = "SmsRelayBootstrapApi"
    }
}
