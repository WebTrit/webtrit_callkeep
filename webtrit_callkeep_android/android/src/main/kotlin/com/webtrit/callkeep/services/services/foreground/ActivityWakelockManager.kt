package com.webtrit.callkeep.services.services.foreground

import android.app.Activity
import android.view.WindowManager
import com.webtrit.callkeep.common.ActivityProvider
import com.webtrit.callkeep.common.Log

class ActivityWakelockManager(
    private val activityProvider: ActivityProvider,
) {
    private val operationQueue = mutableListOf<(Activity) -> Unit>()

    @Volatile private var isScreenOnDesired = false

    // Reference to the listener for unsubscribing later
    private val activityChangeListener: (Activity?) -> Unit = { activity ->
        val activityName = activity?.componentName?.shortClassName ?: "null"
        logger.d("Activity lifecycle change detected. Current Activity: $activityName")

        activity?.let {
            if (isScreenOnDesired) {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            executePendingOperations(it)
        }
    }

    init {
        logger.d("Initializing ActivityWakelockManager")
        activityProvider.addActivityChangeListener(activityChangeListener)
    }

    /**
     * Keeps the screen on by applying the FLAG_KEEP_SCREEN_ON to the current activity.
     */
    fun acquireScreenWakeLock() {
        isScreenOnDesired = true
        executeOrQueue("Acquire FLAG_KEEP_SCREEN_ON") { activity ->
            logger.v("Applying window flag FLAG_KEEP_SCREEN_ON to ${activity.componentName.shortClassName}")
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Releases the wake lock by clearing the FLAG_KEEP_SCREEN_ON from the current activity.
     */
    fun releaseScreenWakeLock() {
        isScreenOnDesired = false
        executeOrQueue("Release FLAG_KEEP_SCREEN_ON") { activity ->
            logger.v("Clearing window flag FLAG_KEEP_SCREEN_ON from ${activity.componentName.shortClassName}")
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Executes the given operation immediately if Activity is non-null,
     * otherwise queues it for execution when Activity becomes available.
     *
     * @param operationName Description of the operation for logging purposes.
     * @param operation The action to perform on the Activity.
     */
    private fun executeOrQueue(
        operationName: String,
        operation: (Activity) -> Unit,
    ) {
        val currentActivity = activityProvider.getActivity()

        if (currentActivity != null) {
            val activityName = currentActivity.componentName.shortClassName
            logger.d("Executing operation: [$operationName] immediately on $activityName")
            operation(currentActivity)
        } else {
            operationQueue.add(operation)
            logger.d(
                "Activity unavailable. Operation [$operationName] queued. Pending operations count: ${operationQueue.size}",
            )
        }
    }

    /**
     * Executes all pending operations from the queue when Activity becomes available.
     */
    private fun executePendingOperations(activity: Activity) {
        if (operationQueue.isEmpty()) return

        logger.d(
            "Flushing operation queue. Executing ${operationQueue.size} pending operations on ${activity.componentName.shortClassName}",
        )

        val iterator = operationQueue.iterator()
        while (iterator.hasNext()) {
            val operation = iterator.next()
            try {
                operation(activity)
            } catch (e: Exception) {
                logger.e(
                    "Failed to execute pending operation on ${activity.componentName.shortClassName}",
                    e,
                )
            }
            iterator.remove()
        }
    }

    /**
     * Disposes of the WakelockManager by unsubscribing from ActivityHolder updates.
     * Forcefully clears the keep-screen-on flag before disposing resources to prevent leaks.
     */
    fun dispose() {
        logger.d("Disposing ActivityWakelockManager. Cleanup started.")

        isScreenOnDesired = false

        activityProvider.getActivity()?.let { activity ->
            runCatching {
                logger.v("Force clearing FLAG_KEEP_SCREEN_ON on ${activity.componentName.shortClassName}")
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }.onFailure { e ->
                logger.e("Failed to clear flag during dispose", e)
            }
        }

        operationQueue.clear()
        activityProvider.removeActivityChangeListener(activityChangeListener)
    }

    companion object {
        private const val TAG = "ActivityWakelockManager"
        private val logger = Log(TAG)
    }
}
