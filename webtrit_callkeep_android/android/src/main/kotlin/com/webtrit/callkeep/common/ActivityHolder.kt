package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.models.CallMetadata

@SuppressLint("StaticFieldLeak")
object ActivityHolder : ActivityProvider {
    private var lifecycle: Lifecycle.Event? = null
    private var activity: Activity? = null

    private val activityChangeListeners = mutableListOf<(Activity?) -> Unit>()

    private const val TAG = "ActivityHolder"

    override fun getActivity(): Activity? {
        return activity
    }

    fun setActivity(newActivity: Activity?) {
        if (activity != newActivity) {
            activity = newActivity
            notifyActivityChanged(newActivity)
        }
    }

    fun getActivityState(): Lifecycle.Event {
        return lifecycle ?: Lifecycle.Event.ON_ANY
    }

    fun setLifecycle(event: Lifecycle.Event) {
        lifecycle = event
    }

    fun isActivityVisible(): Boolean {
        return lifecycle == Lifecycle.Event.ON_RESUME && activity != null
    }

    fun start(metadata: CallMetadata?, context: Context) {
        val hostAppActivity = Platform.getLaunchActivity(context)?.apply {
            data = metadata?.getCallUri()
            // Ensures the activity is started in a new task if needed (required when launching from a service)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    // Prevents recreating the activity if it's already at the top of the task
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    // Brings the existing activity to the foreground instead of creating a new one
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        }

        context.startActivity(hostAppActivity)
    }

    fun finish() {
        Log.i(TAG, "Finishing activity")

        // Using moveTaskToBack(true) instead of finish(), because finish()
        // may cause the error "Error broadcast intent callback: result=CANCELLED".
        // This happens when the activity is finished while handling
        // a notification or a BroadcastReceiver, which cancels relevant operations.
        // moveTaskToBack(true) simply moves the app to the background,
        // preserving all active processes.
        // Reference: https://stackoverflow.com/questions/39480931/error-broadcast-intent-callback-result-cancelled-forintent-act-com-google-and
        activity?.moveTaskToBack(true)
    }


    override fun addActivityChangeListener(listener: (Activity?) -> Unit) {
        activityChangeListeners.add(listener)
    }

    override fun removeActivityChangeListener(listener: (Activity?) -> Unit) {
        activityChangeListeners.remove(listener)
    }

    private fun notifyActivityChanged(newActivity: Activity?) {
        activityChangeListeners.forEach { it.invoke(newActivity) }
    }
}
