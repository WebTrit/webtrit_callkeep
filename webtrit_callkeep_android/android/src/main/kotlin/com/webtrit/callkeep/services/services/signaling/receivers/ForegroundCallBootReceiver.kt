package com.webtrit.callkeep.services.services.signaling.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.*
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.services.signaling.SignalingIsolateService
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver that triggers the startup of the [SignalingIsolateService] on device boot.
 *
 * ## Behavior by Android Version:
 * - **Android 13 and below (API < 34):** Allowed to start foreground services (FGS) from BOOT_COMPLETED.
 * - **Android 14 and above (API 34+):** FGS for phone calls cannot be started from BOOT_COMPLETED.
 *   In this case, the service must be started manually from [WebtritCallkeepPlugin.onAttachedToActivity].
 *
 * ## Notes:
 * - The service will only start if signaling is enabled and push notifications are not in use.
 * - The receiver responds to system boot events and package replacement events.
 */
class ForegroundCallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()

        // Skip startup if signaling service is disabled by user or replaced with push notifications
        if (!StorageDelegate.SignalingService.isSignalingServiceEnabled(context)) {
            Log.i(TAG, "Signaling service is disabled, skipping")
            return
        }

        if (action !in listOf(
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                ACTION_QUICKBOOT_POWERON
            )
        ) {
            Log.w(TAG, "Unhandled broadcast action: $action")
            return
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "PhoneCall-type FGS not allowed to start from BOOT_COMPLETED on Android 14+")
            return
        }

        enqueueSignalingWorker(context)
    }

    /**
     * Schedules a one-time worker via [WorkManager] to start the signaling service.
     * A slight delay is added to ensure the system is ready.
     */
    private fun enqueueSignalingWorker(context: Context) {

        val workRequest = OneTimeWorkRequestBuilder<SignalingStartWorker>().setInitialDelay(2, TimeUnit.SECONDS).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest
        )
    }

    companion object {
        private const val TAG = "ForegroundCallBootReceiver"
        private const val WORK_NAME = "SignalingStartWorker"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}

/**
 * A [CoroutineWorker] responsible for launching [SignalingIsolateService] in the background
 * after system boot or package update, using [WorkManager].
 *
 * This ensures the service is launched reliably without conflicting with boot-time restrictions.
 */
class SignalingStartWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val serviceIntent = Intent(applicationContext, SignalingIsolateService::class.java)
            ContextCompat.startForegroundService(applicationContext, serviceIntent)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SignalingIsolateService:  ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SignalingStartWorker"
    }
}
