package com.webtrit.callkeep.api.background

import android.content.Context
import android.util.Log

import com.webtrit.callkeep.FlutterLog
import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.services.NotificationService
import com.webtrit.callkeep.services.AudioService

/**
 * This class acts as a proxy for handling telephony-related operations in cases where the actual telephony module is not available.
 * It manages call-related notifications, ending calls, answering calls, and user interactions with the notifications.
 *
 * @param context The Android application context.
 * @param api The Flutter API delegate for communication with the Flutter application.
 */
class ProxyBackgroundCallkeepApi(
    private val context: Context,
    private val api: PDelegateBackgroundServiceFlutterApi,
) : BackgroundCallkeepApi {
    private val notificationService = NotificationService(context)
    private val audioService = AudioService(context)

    /**
     * Registers a broadcast receiver to listen for events.
     */
    override fun register() {
        Log.d(TAG, "ProxyBackgroundCallkeepApi:register")
    }

    /**
     * Unregisters a broadcast receiver to listen for events.
     */
    override fun unregister() {
        Log.d(TAG, "ProxyBackgroundCallkeepApi:unregister")
    }

    /**
     * Initiates an incoming call notification for the specified call metadata.
     * This method displays a notification for the incoming call and provides an option to answer the call.
     *
     * @param metadata The metadata of the incoming call.
     * @param callback A callback function to be invoked after displaying the notification.
     */
    override fun incomingCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        notificationService.showIncomingCallNotification(metadata, hasAnswerButton = false)
        audioService.startRingtone(metadata.ringtonePath)
        callback.invoke(Result.success(Unit))
    }

    /**
     * Ends an ongoing call and cancels the active notification.
     *
     * @param metadata The metadata of the call to be ended.
     */
    override fun endCall(metadata: CallMetadata) {
        notificationService.cancelActiveNotification()
        audioService.stopRingtone()
        // Perform end call action (custom logic)
        api.performEndCall(metadata.callId) {}
    }

    override fun endAllCalls() {
        FlutterLog.d(TAG, "endAllCalls")
    }

    /**
     * Answers an incoming call by launching the app's main activity.
     * This method is called when the user taps on the incoming call notification.
     *
     * @param metadata The metadata of the call to be answered.
     */
    override fun answer(metadata: CallMetadata) {
        if (!ActivityHolder.isActivityVisible()) {
            context.startActivity(Platform.getLaunchActivity(context)?.apply {
                data = metadata.getCallUri()
            })
        }
    }

    /**
     * Hangs up an ongoing call and cancels the active notification.
     *
     * @param metadata The metadata of the call to be hung up.
     * @param callback A callback function to be invoked after hanging up the call.
     */
    override fun hungUp(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        notificationService.cancelActiveNotification()
        audioService.stopRingtone()
        callback(Result.success(Unit))
    }

    companion object {
        private const val TAG = "ProxyBackgroundCallkeepApi"
    }
}
