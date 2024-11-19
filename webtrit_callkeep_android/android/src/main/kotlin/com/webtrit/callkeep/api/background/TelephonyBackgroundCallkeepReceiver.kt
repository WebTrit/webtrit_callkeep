import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import io.flutter.Log

import com.webtrit.callkeep.common.FlutterLog
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.api.background.ReportAction
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.models.CallMetadata

/**
 * This class represents a BroadcastReceiver for handling telephony call-related events in the background.
 * It is responsible for listening to specific broadcast actions and notifying the Flutter API when calls are accepted or missed.
 *
 * @param api The Flutter API service that handles communication with the Flutter application.
 */
class TelephonyBackgroundCallkeepReceiver(
    private val api: PDelegateBackgroundServiceFlutterApi,
    private val context: Context,
) : BroadcastReceiver() {

    private var isReceiverRegistered = false

    /**
     * Registers this receiver with the provided Android context.
     *
     * @param context The Android context in which to register the receiver.
     */
    fun registerReceiver() {
        Log.d(TAG, "TelephonyBackgroundCallkeepReceiver:registerReceiver")

        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ReportAction.AcceptedCall.action)
            intentFilter.addAction(ReportAction.HungUp.action)
            intentFilter.addAction(ReportAction.MissedCall.action)

            context.registerCustomReceiver(this, intentFilter)
            isReceiverRegistered = true
        }
    }

    fun unregisterReceiver() {
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
            ReportAction.HungUp.action -> handleHungUpCall(intent.extras)
            ReportAction.MissedCall.action -> handleMissedCall(intent.extras)
        }
    }

    private fun handleAcceptedCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            api.performAnswerCall(
                metadata.callId,
            ) {}
        }
    }

    private fun handleHungUpCall(extras: Bundle?) {
        FlutterLog.i(TAG, "handleHungUpCall: $extras ")

        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            api.performEndCall(
                metadata.callId,
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
                metadata.hasVideo,
                metadata.createdTime ?: System.currentTimeMillis(),
                null,
                System.currentTimeMillis()
            ) {}
        }
    }

    companion object {
        private const val TAG = "TelephonyBackgroundCallkeepReceiver"
    }
}
