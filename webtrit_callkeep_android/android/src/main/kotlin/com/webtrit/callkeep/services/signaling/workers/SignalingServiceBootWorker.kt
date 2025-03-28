package com.webtrit.callkeep.services.signaling.workers

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.*
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.services.signaling.SignalingIsolateService
import java.util.concurrent.TimeUnit

class SignalingServiceBootWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return try {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, SignalingIsolateService::class.java)
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: $e")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ForegroundCallWorker"
        private const val ACTION_RESTART_FOREGROUND_SERVICE =
            "id.flutter.webtrit.foreground_call_service.ACTION_RESTART_FOREGROUND_SERVICE"

        fun enqueue(context: Context, delayInMillis: Long = 15000) {
            val workRequest =
                OneTimeWorkRequestBuilder<SignalingServiceBootWorker>().addTag(ACTION_RESTART_FOREGROUND_SERVICE)
                    .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ACTION_RESTART_FOREGROUND_SERVICE, ExistingWorkPolicy.REPLACE, workRequest
            )
        }

        fun remove(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(ACTION_RESTART_FOREGROUND_SERVICE)
        }
    }
}