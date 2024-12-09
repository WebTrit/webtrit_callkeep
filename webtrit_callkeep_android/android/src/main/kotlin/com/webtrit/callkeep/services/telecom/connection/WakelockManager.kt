package com.webtrit.callkeep.services.telecom.connection

import android.app.Activity
import android.view.WindowManager
import com.webtrit.callkeep.common.ActivityHolder

class WakelockManager {
    /**
     * Keeps the screen on by applying the FLAG_KEEP_SCREEN_ON to the current activity.
     */
    fun acquireWakeLock() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Releases the wake lock by clearing the FLAG_KEEP_SCREEN_ON from the current activity.
     */
    fun releaseWakeLock() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Gets the current activity from ActivityHolder.
     */
    private val activity: Activity?
        get() = ActivityHolder.getActivity()
}