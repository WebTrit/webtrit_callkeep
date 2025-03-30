package com.webtrit.callkeep.services.broadcaster

import android.content.Intent
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus

object SignalingStatusBroadcaster {
    private const val TAG = "SignalingStatusDispatcher"

    const val ACTION_STATUS_CHANGED = "com.webtrit.callkeep.SIGNALING_STATUS_CHANGED"

    private var status: SignalingStatus? = null

    val currentStatus: SignalingStatus?
        get() = status

    fun setStatus(newStatus: SignalingStatus) {
        status = newStatus
        notifyStatusChanged(newStatus)
    }

    private fun notifyStatusChanged(status: SignalingStatus) {
        val context = ContextHolder.context
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtras(status.toBundle())
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.i(TAG, "Failed to send broadcast: $e")
        }
    }
}
