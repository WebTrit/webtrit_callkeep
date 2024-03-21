import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log

import com.webtrit.callkeep.webtrit_callkeep_android.FlutterLog
import com.webtrit.callkeep.webtrit_callkeep_android.PDelegateAndroidServiceFlutterApi
import com.webtrit.callkeep.webtrit_callkeep_android.R
import com.webtrit.callkeep.webtrit_callkeep_android.api.background.ReportAction
import com.webtrit.callkeep.webtrit_callkeep_android.common.ApplicationData
import com.webtrit.callkeep.webtrit_callkeep_android.common.helpers.Platform
import com.webtrit.callkeep.webtrit_callkeep_android.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.webtrit_callkeep_android.common.models.CallMetadata

/**
 * This class represents a BroadcastReceiver for handling telephony call-related events in the background.
 * It is responsible for listening to specific broadcast actions and notifying the Flutter API when calls are accepted or missed.
 *
 * @param api The Flutter API service that handles communication with the Flutter application.
 */
class TelephonyBackgroundCallkeepReceiver(
    private val api: PDelegateAndroidServiceFlutterApi,
    private val context: Context,
) : BroadcastReceiver() {

    private var isReceiverRegistered = false

    /**
     * Registers this receiver with the provided Android context.
     *
     * @param context The Android context in which to register the receiver.
     */
    fun registerReceiver(context: Context) {
        Log.d(TAG, "TelephonyBackgroundCallkeepReceiver:registerReceiver")

        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ReportAction.AcceptedCall.action)
            intentFilter.addAction(ReportAction.MissedCall.action)

            context.registerCustomReceiver(this, intentFilter)
            isReceiverRegistered = true
        }
    }

    fun unregisterReceiver(context: Context) {
        Log.d(TAG, "TelephonyBackgroundCallkeepReceiver:unRegisterReceiver")
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        FlutterLog.i(TAG, "onReceive action - ${intent?.action} ")

        when (intent?.action) {
            ReportAction.AcceptedCall.action -> handleAcceptedCall(intent.extras)
            ReportAction.MissedCall.action -> handleMissedCall(intent.extras)
        }
    }

    private fun handleAcceptedCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            if (!ApplicationData.isActivityVisible()) {
                FlutterLog.i(TAG, "Activity is not visible, launch activity")
                val hostAppActivity = Platform.getLaunchActivity(context)?.apply {
                    data = metadata.getCallUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }

                PendingIntent.getActivity(
                    context,
                    R.integer.notification_incoming_call_id,
                    hostAppActivity,
                    PendingIntent.FLAG_IMMUTABLE
                ).send()
            }

            // Notify the Flutter API that a call was accepted
            api.endCallReceived(
                metadata.callId,
                metadata.number,
                metadata.isVideo,
                metadata.createdTime!!,
                System.currentTimeMillis(),
                null,
            ) {}
        }
    }

    private fun handleMissedCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            // Notify the Flutter API that a call was missed
            api.endCallReceived(
                metadata.callId,
                metadata.number,
                metadata.isVideo,
                metadata.createdTime!!,
                null,
                System.currentTimeMillis()
            ) {}
        }
    }

    companion object {
        private const val TAG = "TelephonyBackgroundCallkeepReceiver"
    }
}
