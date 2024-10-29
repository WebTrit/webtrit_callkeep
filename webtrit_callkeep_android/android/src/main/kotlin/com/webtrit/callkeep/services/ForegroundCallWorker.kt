package com.webtrit.callkeep.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.flutter.Log
import java.util.concurrent.TimeUnit

class ForegroundCallWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return try {
            ContextCompat.startForegroundService(
                applicationContext, Intent(applicationContext, ForegroundCallService::class.java)
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("ForegroundCallWorker", "Failed to start service", e)
            Result.retry()
        }
    }

    companion object {
        private const val ACTION_RESTART_FOREGROUND_SERVICE =
            "id.flutter.webtrit.foreground_call_service.ACTION_RESTART_FOREGROUND_SERVICE"

        fun enqueue(context: Context, delayInMillis: Long = 15000) {
            val workRequest =
                OneTimeWorkRequestBuilder<ForegroundCallWorker>().addTag(ACTION_RESTART_FOREGROUND_SERVICE)
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
