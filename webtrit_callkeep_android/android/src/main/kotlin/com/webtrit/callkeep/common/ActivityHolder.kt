package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.models.CallMetadata

@SuppressLint("StaticFieldLeak")
object ActivityHolder {
    private var lifecycle: Lifecycle.Event? = null
    private var activity: Activity? = null

    private val activityChangeListeners = mutableListOf<(Activity?) -> Unit>()

    fun getActivity(): Activity? {
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(hostAppActivity)
    }

    fun finish() {
        activity?.moveTaskToBack(true)
        activity?.finish()
    }

    fun addActivityChangeListener(listener: (Activity?) -> Unit) {
        activityChangeListeners.add(listener)
    }

    fun removeActivityChangeListener(listener: (Activity?) -> Unit) {
        activityChangeListeners.remove(listener)
    }

    private fun notifyActivityChanged(newActivity: Activity?) {
        activityChangeListeners.forEach { it.invoke(newActivity) }
    }
}
