package com.webtrit.callkeep.services.telecom.connection

import android.app.Activity
import android.view.WindowManager
import com.webtrit.callkeep.common.ActivityHolder

class ScreenWakelockManager {
    private val operationQueue = mutableListOf<(Activity) -> Unit>()

    // Reference to the listener for unsubscribing later
    private val activityChangeListener: (Activity?) -> Unit = { activity ->
        activity?.let { executePendingOperations(it) }
    }

    init {
        // Subscribe to ActivityHolder changes
        ActivityHolder.addActivityChangeListener(activityChangeListener)
    }

    /**
     * Keeps the screen on by applying the FLAG_KEEP_SCREEN_ON to the current activity.
     */
    fun acquireScreenWakeLock() {
        executeOrQueue { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    /**
     * Releases the wake lock by clearing the FLAG_KEEP_SCREEN_ON from the current activity.
     */
    fun releaseScreenWakeLock() {
        executeOrQueue { it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    /**
     * Executes the given operation immediately if Activity is non-null,
     * otherwise queues it for execution when Activity becomes available.
     */
    private fun executeOrQueue(operation: (Activity) -> Unit) {
        val currentActivity = ActivityHolder.getActivity()
        if (currentActivity != null) {
            operation(currentActivity)
        } else {
            operationQueue.add(operation)
        }
    }

    /**
     * Executes all pending operations from the queue when Activity becomes available.
     */
    private fun executePendingOperations(activity: Activity) {
        val iterator = operationQueue.iterator()
        while (iterator.hasNext()) {
            val operation = iterator.next()
            operation(activity)
            iterator.remove()
        }
    }

    /**
     * Disposes of the WakelockManager by unsubscribing from ActivityHolder updates.
     */
    fun dispose() {
        ActivityHolder.removeActivityChangeListener(activityChangeListener)
    }
}
