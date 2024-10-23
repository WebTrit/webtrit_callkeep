package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.models.CallMetadata

@SuppressLint("StaticFieldLeak")
object ActivityHolder {
    private var lifecycle: Lifecycle.Event? = null
    private var activity: Activity? = null

    fun getActivity(): Activity? {
        return activity
    }

    fun setActivity(activity: Activity?) {
        ActivityHolder.activity = activity
    }

    fun getActivityState(): Lifecycle.Event? {
        return lifecycle
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
        context.startActivity(hostAppActivity);

    }

    fun finish() {
        activity?.moveTaskToBack(true)
        activity?.finish()
    }
}