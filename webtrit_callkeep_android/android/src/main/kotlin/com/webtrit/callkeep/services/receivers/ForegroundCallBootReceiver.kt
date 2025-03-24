package com.webtrit.callkeep.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.services.SignalingIsolateService

class ForegroundCallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_QUICKBOOT_POWERON) {
            val wakeLock = SignalingIsolateService.Companion.getLock(context)
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(10 * 60 * 1000L /*10 minutes*/)
                }
            }

            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, SignalingIsolateService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            Log.w(TAG, "Received unexpected action: $action")
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val TAG = "ForegroundCallBootReceiver"
    }
}