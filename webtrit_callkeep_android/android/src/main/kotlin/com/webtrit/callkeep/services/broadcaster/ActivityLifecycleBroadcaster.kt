package com.webtrit.callkeep.services.broadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.common.toBundle

/**
 * This object is responsible for broadcasting the lifecycle events of the activity.
 * The object holds the current lifecycle event and notifies any interested parties when it changes.
 */
object ActivityLifecycleBroadcaster {
    const val ACTION = "ActivityLifecycleBroadcaster.LIFECYCLE_EVENT"

    private var value: Lifecycle.Event? = null

    val currentValue: Lifecycle.Event?
        get() = value

    fun setValue(context: Context, newValue: Lifecycle.Event) {
        value = newValue
        notifyValueChanged(context, newValue)
    }

    fun register(context: Context, receiver: BroadcastReceiver) {
        context.registerReceiverCompat(receiver, IntentFilter(ACTION))
    }

    fun unregister(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    private fun notifyValueChanged(context: Context, value: Lifecycle.Event) {
        context.applicationContext.sendInternalBroadcast(ACTION, value.toBundle())
    }
}
