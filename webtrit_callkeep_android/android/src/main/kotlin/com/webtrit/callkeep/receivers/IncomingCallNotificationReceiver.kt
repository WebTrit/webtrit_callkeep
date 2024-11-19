package com.webtrit.callkeep.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction

class IncomingCallNotificationReceiver(
    private val context: Context,
    private val endCall: (CallMetadata) -> Unit,
    private val answerCall: (CallMetadata) -> Unit
) : BroadcastReceiver() {
    private var isReceiverRegistered = false

    fun registerReceiver() {
        if (!isReceiverRegistered) {
            Log.i(TAG, "Registering receiver")
            // Register actions from notification
            val notificationsReceiverFilter = IntentFilter().apply {
                addAction(NotificationAction.Hangup.action)
                addAction(NotificationAction.Answer.action)
            }
            context.registerCustomReceiver(this, notificationsReceiverFilter)
            isReceiverRegistered = true
        } else {
            Log.i(TAG, "Receiver already registered")
        }
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(this)
            isReceiverRegistered = false
            Log.i(TAG, "Receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callMetaData = CallMetadata.fromBundle(intent.extras!!)
        when (intent.action) {
            NotificationAction.Hangup.action -> endCall(callMetaData)
            NotificationAction.Answer.action -> answerCall(callMetaData)
            else -> Log.i(TAG, "Unknown action received: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "IncomingCallNotificationReceiver"
    }
}
