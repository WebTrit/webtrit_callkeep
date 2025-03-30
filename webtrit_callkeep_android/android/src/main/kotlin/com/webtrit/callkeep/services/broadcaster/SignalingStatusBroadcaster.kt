package com.webtrit.callkeep.services.broadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.models.SignalingStatus

/**
 * This object is responsible for broadcasting the signaling status events.
 * The object holds the current signaling status and notifies any interested parties when it changes.
 */
object SignalingStatusBroadcaster {
    const val ACTION = "SignalingStatusBroadcaster.SIGNALING_STATUS"

    private var value: SignalingStatus? = null

    val currentValue: SignalingStatus?
        get() = value

    fun setValue(context: Context, newValue: SignalingStatus) {
        value = newValue
        notifyValueChanged(context, newValue)
    }

    fun register(context: Context, receiver: BroadcastReceiver) {
        context.registerReceiverCompat(receiver, IntentFilter(ACTION))
    }

    fun unregister(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    private fun notifyValueChanged(context: Context, value: SignalingStatus) {
        context.applicationContext.sendInternalBroadcast(ACTION, value.toBundle())
    }
}
