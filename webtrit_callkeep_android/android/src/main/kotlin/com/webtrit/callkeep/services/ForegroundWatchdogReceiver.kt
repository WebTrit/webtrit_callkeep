package com.webtrit.callkeep.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.flutter.Log
import java.util.concurrent.TimeUnit

class ForegroundWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESPAWN) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ForegroundCallService::class.java))
            } catch (e: Exception) {
                Log.e("ForegroundWatchdogReceiver", "Failed to start service", e)
            }
        }
    }

    companion object {
        private const val ACTION_RESPAWN = "id.flutter.background_service.RESPAWN"

        fun enqueue(context: Context, delayInMillis: Long = 5000) {
            val workRequest = OneTimeWorkRequestBuilder<ForegroundCallWorker>().addTag(ACTION_RESPAWN)
                .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS).build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }

        fun remove(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(ACTION_RESPAWN)
        }
    }
}

class ForegroundCallWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return try {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, ForegroundCallService::class.java)
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("ForegroundCallWorker", "Failed to start service", e)
            Result.retry()
        }
    }
}
