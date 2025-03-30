package com.webtrit.callkeep.services.broadcaster

import android.content.Intent
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.toBundle

/**
 * This object is responsible for broadcasting the lifecycle events of the activity.
 * The object holds the current lifecycle event and notifies any interested parties when it changes.
 */
object ActivityLifecycleBroadcaster {
    private const val TAG = "ActivityLifecycleBroadcaster"

    const val ACTION_VALUE_CHANGED = "ACTIVITY_LIFECYCLE_EVENT_CHANGED"

    private var value: Lifecycle.Event? = null

    val currentValue: Lifecycle.Event?
        get() = value

    fun setValue(newValue: Lifecycle.Event) {
        value = newValue
        notifyValueChanged(newValue)
    }

    private fun notifyValueChanged(value: Lifecycle.Event) {
        val context = ContextHolder.context
        val intent = Intent(ACTION_VALUE_CHANGED).apply {
            putExtras(value.toBundle())
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.i(TAG, "Failed to send broadcast: $e")
        }
    }
}
