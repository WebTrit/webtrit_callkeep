package com.webtrit.callkeep.services.broadcaster

import android.content.Intent
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus

/**
 * This object is responsible for broadcasting the signaling status events.
 * The object holds the current signaling status and notifies any interested parties when it changes.
 */
object SignalingStatusBroadcaster {
    private const val TAG = "SignalingStatusDispatcher"

    const val ACTION_VALUE_CHANGED = "SIGNALING_STATUS_CHANGED"

    private var value: SignalingStatus? = null

    val currentValue: SignalingStatus?
        get() = value

    fun setValue(newStatus: SignalingStatus) {
        value = newStatus
        notifyValueChanged(newStatus)
    }

    private fun notifyValueChanged(status: SignalingStatus) {
        val context = ContextHolder.context
        val intent = Intent(ACTION_VALUE_CHANGED).apply {
            putExtras(status.toBundle())
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.i(TAG, "Failed to send broadcast: $e")
        }
    }
}
