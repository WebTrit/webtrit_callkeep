package com.webtrit.callkeep.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat

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
        private const val QUEUE_REQUEST_CODE = 100
        private const val ACTION_RESPAWN = "id.flutter.background_service.RESPAWN"

        fun enqueue(context: Context, millis: Int = 5000) {
            val intent = Intent(context, ForegroundWatchdogReceiver::class.java)
            intent.setAction(ACTION_RESPAWN)
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pIntent = PendingIntent.getBroadcast(context, QUEUE_REQUEST_CODE, intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AlarmManagerCompat.setAndAllowWhileIdle(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + millis,
                    pIntent
                )
            } else {
                AlarmManagerCompat.setExact(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + millis,
                    pIntent
                )
            }
        }

        fun remove(context: Context) {
            val intent = Intent(context, ForegroundWatchdogReceiver::class.java)
            intent.setAction(ACTION_RESPAWN)

            var flags = PendingIntent.FLAG_CANCEL_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pi = PendingIntent.getBroadcast(context, QUEUE_REQUEST_CODE, intent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pi)
        }
    }
}
