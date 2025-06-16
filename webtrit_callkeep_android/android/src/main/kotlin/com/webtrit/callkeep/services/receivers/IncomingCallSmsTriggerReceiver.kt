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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefix = StorageDelegate.IncomingCallSmsConfig.getSmsPrefix(context) ?: return
        val pattern = StorageDelegate.IncomingCallSmsConfig.getRegexPattern(context) ?: return
        val regex = runCatching { Regex(pattern) }.getOrElse {
            Log.e(TAG, "Invalid regex: $pattern, error: ${it.message}")
            return
        }

        extractValidSmsMessages(context, intent, prefix, regex).forEach {
            tryStartCall(context, it)
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

        return messages.mapNotNull { message ->
            val body = message.messageBody ?: return@mapNotNull null
            if (!body.startsWith(prefix)) return@mapNotNull null

            val match = regex.find(body) ?: return@mapNotNull null
            val (callId, handle, displayNameEncoded, hasVideoStr) = match.destructured
            val displayName = URLDecoder.decode(displayNameEncoded, "UTF-8")

            CallMetadata(
                callId = callId,
                handle = CallHandle(handle),
                displayName = displayName,
                hasVideo = hasVideoStr == "true",
                ringtonePath = StorageDelegate.Sound.getRingtonePath(context)
            )
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
