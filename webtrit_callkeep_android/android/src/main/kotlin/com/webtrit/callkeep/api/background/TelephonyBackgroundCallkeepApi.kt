package com.webtrit.callkeep.api.background

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

import com.webtrit.callkeep.connection.PhoneConnectionService
import com.webtrit.callkeep.common.models.CallMetadata

import TelephonyBackgroundCallkeepReceiver
import com.webtrit.callkeep.FlutterLog
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi

/**
 * This class provides an API for handling telephony-related operations in the background.
 * It interacts with the Flutter API and manages incoming and outgoing calls.
 *
 * @param context The Android application context.
 * @param delegate The Flutter API delegate for communication with the Flutter application.
 */
class TelephonyBackgroundCallkeepApi(
    private val context: Context,
    private val delegate: PDelegateBackgroundServiceFlutterApi,
) : BackgroundCallkeepApi {

    private val flutterDelegate = TelephonyBackgroundCallkeepReceiver(delegate, context)

    /**
     * Initializes the TelephonyBackgroundCallkeepApi by registering the FlutterDelegate receiver.
     */
    override fun register() {
        flutterDelegate.registerReceiver(context)
    }

    /**
     * Unregisters a broadcast receiver to listen for events.
     */
    override fun unregister() {
        Log.d(TAG, "TelephonyBackgroundCallkeepApi:unregister")
        flutterDelegate.unregisterReceiver(context)
    }

    /**
     * Initiates an incoming call with the specified call metadata.
     *
     * @param metadata The metadata of the incoming call.
     * @param callback A callback function to be invoked after initiating the call.
     */
    override fun incomingCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.startIncomingCall(context, metadata)
        callback.invoke(Result.success(Unit))
    }

    /**
     * Ends an ongoing call with the specified call metadata.
     *
     * @param metadata The metadata of the call to be ended.
     */
    override fun endCall(metadata: CallMetadata) {
        delegate.performEndCall(metadata.callId) {}
        PhoneConnectionService.startHungUpCall(context, metadata)
    }

    override fun endAllCalls() {
        FlutterLog.d(TAG, "endAllCalls")

        //TODO: Rename this notifyAboutDetachActivity to  endAllCalls
        PhoneConnectionService.notifyAboutDetachActivity(context)
    }

    /**
     * Answers an incoming call with the specified call metadata.
     *
     * @param metadata The metadata of the call to be answered.
     */
    override fun answer(metadata: CallMetadata) {
        Log.d(TAG, "TelephonyBackgroundCallkeepApi:answer")
        PhoneConnectionService.startAnswerCall(context, metadata)
    }

    /**
     * Hangs up an ongoing call with the specified call metadata.
     *
     * @param metadata The metadata of the call to be hung up.
     * @param callback A callback function to be invoked after hanging up the call.
     */
    override fun hungUp(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.startDeclineCall(context, metadata)
        callback(Result.success(Unit))
    }

    companion object {
        private const val TAG = "TelephonyBackgroundCallkeepApi"

        /**
         * Notifies the system of a missed incoming call event.
         *
         * @param context The Android application context.
         * @param metadata The metadata of the missed call.
         */
        fun notifyMissedIncomingCall(context: Context, metadata: CallMetadata) {
            notify(context, ReportAction.MissedCall.action, metadata.toBundle())
        }

        /**
         * Notifies the system of an accepted incoming call event.
         *
         * @param context The Android application context.
         * @param metadata The metadata of the accepted call.
         */
        fun notifyAnswer(context: Context, metadata: CallMetadata) {
            notify(context, ReportAction.AcceptedCall.action, metadata.toBundle())
        }

        private fun notify(context: Context, action: String, attribute: Bundle? = null) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                val intent = Intent(action)
                if (attribute != null) {
                    intent.putExtras(attribute)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
