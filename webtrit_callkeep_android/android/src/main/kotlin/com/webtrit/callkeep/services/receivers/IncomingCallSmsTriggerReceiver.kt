package com.webtrit.callkeep.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallHandle
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import java.net.URLDecoder

class IncomingCallSmsTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        ContextHolder.init(context)

        val prefix = StorageDelegate.IncomingCallSmsConfig.getSmsPrefix(context) ?: return
        val pattern = StorageDelegate.IncomingCallSmsConfig.getRegexPattern(context) ?: return
        val regex = runCatching { Regex(pattern) }.getOrElse {
            Log.e(TAG, "Invalid regex: $pattern, error: ${it.message}")
            return
        }

        val validMessages = extractValidSmsMessages(context, intent, prefix, regex)
        if (validMessages.isEmpty()) {
            Log.e(TAG, "No valid SMS messages found with prefix: $prefix and regex: $regex")
        } else {
            validMessages.forEach {
                tryStartCall(context, it)
            }
        }
    }

    private fun tryStartCall(context: Context, metadata: CallMetadata) {
        try {
            PhoneConnectionService.startIncomingCall(
                context,
                metadata,
                onSuccess = { Log.d(TAG, "Incoming call started") },
                onError = { Log.e(TAG, "Failed to start call: $it") })
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting call: ${e.message}")
        }
    }

    private fun extractValidSmsMessages(
        context: Context, intent: Intent, prefix: String, regex: Regex
    ): List<CallMetadata> {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()

        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (!fullBody.contains(prefix)) return emptyList()

        val match = regex.find(fullBody) ?: return emptyList()

        val (callId, handleValue, displayNameEncoded, hasVideoStr) = match.destructured

        return listOf(
            CallMetadata(
                callId = callId,
                handle = CallHandle(handleValue),
                displayName = URLDecoder.decode(displayNameEncoded, "UTF-8"),
                hasVideo = hasVideoStr == "true",
                ringtonePath = StorageDelegate.Sound.getRingtonePath(context)
            )
        )
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
