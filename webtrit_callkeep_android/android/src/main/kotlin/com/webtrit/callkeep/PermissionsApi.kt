package com.webtrit.callkeep

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.BatteryModeHelper
import com.webtrit.callkeep.common.PermissionsHelper
import com.webtrit.callkeep.common.toAndroidPermissions
import com.webtrit.callkeep.common.toPPermissionResults
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.TimeoutException

class PermissionsApi(
    private val context: Context,
) : PHostPermissionsApi,
    PluginRegistry.RequestPermissionsResultListener {
    private var pendingPermissionCallback: ((Result<List<PPermissionResult>>) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun getFullScreenIntentPermissionStatus(callback: (Result<PSpecialPermissionStatusTypeEnum>) -> Unit) {
        val screenIntentPermissionAvailable = PermissionsHelper(context).canUseFullScreenIntent()
        val status =
            if (screenIntentPermissionAvailable) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED
        callback.invoke(Result.success(status))
    }

    /**
     * Attempts to open the system settings screen for managing the "Use full screen intent" permission.
     *
     * This setting allows the app to show incoming call UI in full screen when the device is locked.
     * The method internally checks and starts the appropriate system intent.
     *
     * @param callback A callback that receives a [Result]:
     * - [Result.success(Unit)] if the settings screen was successfully opened.
     * - [Result.failure] with an exception (e.g., [android.content.ActivityNotFoundException]) if the intent cannot be handled.
     *
     * Note: This functionality is only supported on Android 13 (API 33) and above,
     * and may not be available on all devices even on supported versions.
     */
    override fun openFullScreenIntentSettings(callback: (Result<Unit>) -> Unit) {
        try {
            PermissionsHelper(context).launchFullScreenIntentSettings()
            callback.invoke(Result.success(Unit))
        } catch (e: Exception) {
            callback.invoke(Result.failure(e))
        }
    }

    /**
     * Attempts to open the common system settings screen
     */
    override fun openSettings(callback: (Result<Unit>) -> Unit) {
        try {
            PermissionsHelper(context).launchSettings()
            callback.invoke(Result.success(Unit))
        } catch (e: Exception) {
            callback.invoke(Result.failure(e))
        }
    }

    override fun getBatteryMode(callback: (Result<PCallkeepAndroidBatteryMode>) -> Unit) {
        val batteryMode = BatteryModeHelper(context)
        val mode =
            when {
                batteryMode.isUnrestricted() -> PCallkeepAndroidBatteryMode.UNRESTRICTED
                batteryMode.isRestricted() -> PCallkeepAndroidBatteryMode.RESTRICTED
                batteryMode.isOptimized() -> PCallkeepAndroidBatteryMode.OPTIMIZED
                else -> PCallkeepAndroidBatteryMode.UNKNOWN
            }
        callback.invoke(Result.success(mode))
    }

    /**
     * Requests the given permissions from the user.
     * @param permissions The list of permissions to request.
     * @param callback A callback that will be invoked with the results of the permission request.
     */
    override fun requestPermissions(
        permissions: List<PCallkeepPermission>,
        callback: (Result<List<PPermissionResult>>) -> Unit,
    ) {
        val activity = ActivityHolder.getActivity()
        if (activity == null) {
            callback.invoke(Result.failure(IllegalStateException("No active Activity")))
            return
        }

        val androidPerms = permissions.toAndroidPermissions()
        val missing =
            androidPerms.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

        if (missing.isEmpty()) {
            val results = androidPerms.toPPermissionResults(context)
            callback.invoke(Result.success(results))
            return
        }

        synchronized(this) {
            if (pendingPermissionCallback != null) {
                callback.invoke(Result.failure(IllegalStateException("A permission request is already in progress")))
                return
            }
            pendingPermissionCallback = callback
        }

        timeoutRunnable =
            Runnable {
                synchronized(this) {
                    if (pendingPermissionCallback != null) {
                        pendingPermissionCallback?.invoke(
                            Result.failure(TimeoutException("User did not accept/deny permissions within $PERMISSION_REQUEST_TIMEOUT_MS ms")),
                        )
                        pendingPermissionCallback = null
                        timeoutRunnable = null
                    }
                }
            }

        handler.postDelayed(timeoutRunnable!!, PERMISSION_REQUEST_TIMEOUT_MS)

        try {
            activity.runOnUiThread {
                ActivityCompat.requestPermissions(
                    activity,
                    missing.toTypedArray(),
                    PERMISSION_REQUEST_CODE,
                )
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable!!)
            timeoutRunnable = null
            pendingPermissionCallback = null
            callback.invoke(Result.failure(e))
        }
    }

    /**
     * Checks the current status of the given permissions without requesting them.
     * @param permissions The list of permissions to check.
     * @param callback A callback that will be invoked with the results of the permission status check.
     */
    override fun checkPermissionsStatus(
        permissions: List<PCallkeepPermission>,
        callback: (Result<List<PPermissionResult>>) -> Unit,
    ) {
        try {
            val androidPerms = permissions.toAndroidPermissions()
            val results = androidPerms.toPPermissionResults(context)
            callback.invoke(Result.success(results))
        } catch (e: Exception) {
            callback.invoke(Result.failure(e))
        }
    }

    /**
     * Handles the result of a permission request.
     * This method is called by the Android system when the user responds to a permission request.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return false
        }

        synchronized(this) {
            val callback = pendingPermissionCallback ?: return false
            timeoutRunnable?.let { handler.removeCallbacks(it) }
            timeoutRunnable = null

            try {
                val results = permissions.toList().toPPermissionResults(context)
                callback.invoke(Result.success(results))
            } catch (e: Exception) {
                callback.invoke(Result.failure(e))
            } finally {
                pendingPermissionCallback = null
            }
        }

        return true
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 10101
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 20_000L
    }
}
