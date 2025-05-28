package com.webtrit.callkeep.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallHandle
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import java.net.URLDecoder

class IncomingCallSmsTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: intent = $intent")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return
        }

        val prefix = StorageDelegate.IncomingCallSmsConfig.getSmsPrefix(context) ?: return
        val pattern = StorageDelegate.IncomingCallSmsConfig.getRegexPattern(context) ?: return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        Log.d(TAG, "Received SMS with prefix: $prefix and regex pattern: $pattern")

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid regex pattern: $pattern, error: ${e.message}")
            return
        }

        for (message in messages) {
            val body = message.messageBody ?: continue

            if (!body.startsWith(prefix)) continue
            Log.d(TAG, "Matched SMS prefix: $prefix — full body: $body")

            val match = regex.find(body) ?: run {
                Log.w(TAG, "Regex did not match body: $body")
                return;
            }

            val (callId, handle, displayNameEncoded, hasVideoStr) = match.destructured
            val displayName = URLDecoder.decode(displayNameEncoded, "UTF-8")

            val metadata = CallMetadata(
                callId = callId,
                handle = CallHandle(handle),
                displayName = displayName,
                hasVideo = hasVideoStr == "true",
                ringtonePath = StorageDelegate.Sound.getRingtonePath(context)
            )

            try {
                PhoneConnectionService.startIncomingCall(
                    context,
                    metadata,
                    onSuccess = { Log.d(TAG, "Incoming call started") },
                    onError = { Log.e(TAG, "Failed to start call: $it") }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting call: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}