package com.webtrit.callkeep

import android.app.Activity
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.isDeviceLockedCompat
import com.webtrit.callkeep.common.moveTaskToBackCompat
import com.webtrit.callkeep.common.setShowWhenLockedCompat
import com.webtrit.callkeep.common.setTurnScreenOnCompat

/**
 * Implements the Pigeon API for controlling Android Activity behavior
 * by delegating logic to Activity/Context extensions.
 *
 * @param activity The current foreground Activity.
 */
class ActivityControlApi(private val activity: Activity) : PHostActivityControlApi {
    companion object {
        private const val TAG = "ActivityControlApi"
        private val logger = Log(TAG)
    }

    /**
     * Allows the app's activity to be shown over the device lock screen.
     */
    override fun showOverLockscreen(
        enable: Boolean,
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.d("showOverLockscreen(enable: $enable) called")
        activity.runOnUiThread {
            try {
                activity.setShowWhenLockedCompat(enable)
                logger.d("showOverLockscreen success")
                callback(Result.success(Unit))
            } catch (e: Exception) {
                logger.e("showOverLockscreen error: ${e.message}")
                callback(Result.failure(e))
            }
        }
    }

    /**
     * Turns the screen on when the app's window is shown.
     */
    override fun wakeScreenOnShow(
        enable: Boolean,
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.d("wakeScreenOnShow(enable: $enable) called")
        activity.runOnUiThread {
            try {
                activity.setTurnScreenOnCompat(enable)
                logger.d("wakeScreenOnShow success")
                callback(Result.success(Unit))
            } catch (e: Exception) {
                logger.e("wakeScreenOnShow error: ${e.message}")
                callback(Result.failure(e))
            }
        }
    }

    /**
     * Moves the entire task (app) to the background.
     */
    override fun sendToBackground(callback: (Result<Boolean>) -> Unit) {
        logger.d("sendToBackground() called")
        activity.runOnUiThread {
            try {
                val result = activity.moveTaskToBackCompat()
                logger.d("sendToBackground success, result: $result")
                callback(Result.success(result))
            } catch (e: Exception) {
                logger.e("sendToBackground error: ${e.message}")
                callback(Result.failure(e))
            }
        }
    }

    /**
     * Checks if the device screen is currently locked (keyguard is active).
     */
    override fun isDeviceLocked(callback: (Result<Boolean>) -> Unit) {
        logger.d("isDeviceLocked() called")
        try {
            // This does not need to run on the UI thread
            val isLocked = activity.isDeviceLockedCompat()
            logger.d("isDeviceLocked success, result: $isLocked")
            callback(Result.success(isLocked))
        } catch (e: Exception) {
            logger.e("isDeviceLocked error: ${e.message}")
            callback(Result.failure(e))
        }
    }
}
