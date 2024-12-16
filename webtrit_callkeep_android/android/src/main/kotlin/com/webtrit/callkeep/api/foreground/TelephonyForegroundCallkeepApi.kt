package com.webtrit.callkeep.api.foreground

import android.app.Activity
import android.content.Context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PCallRequestErrorEnum
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.common.Broadcast
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.receivers.IncomingCallNotificationReceiver

class TelephonyForegroundCallkeepApi(
    private val activity: Activity, flutterDelegateApi: PDelegateFlutterApi
) : ForegroundCallkeepApi {
    private val flutterDelegate = TelephonyForegroundCallkeepReceiver(activity, flutterDelegateApi)
    private val incomingCallReceiver = IncomingCallNotificationReceiver(
        activity,
        endCall = { callMetaData -> endCall(callMetaData) {} },
        answerCall = { callMetaData -> answerCall(callMetaData) {} },
    )

    private var isSetup = false

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "setUp: ${options.android}")

        if (!isSetup) {
            flutterDelegate.registerReceiver(activity)
            incomingCallReceiver.registerReceiver()
            Telecom.registerPhoneAccount(activity)
            StorageDelegate.initIncomingPath(activity, options.android.incomingPath)
            StorageDelegate.initRootPath(activity, options.android.rootPath)
            StorageDelegate.initRingtonePath(activity, options.android.ringtoneSound)
            StorageDelegate.initRingbackPath(activity, options.android.ringbackSound)

            // If an incoming call was answered in the background, retrieve the current new or ringing connection.
            // Extract its metadata and sync the call state with the Flutter side by emitting it as a bundle.
            PhoneConnectionService.connectionManager.getActiveOrPendingConnection()?.metadata?.let {
                flutterDelegate.handleDidPushIncomingCall(it.toBundle())
            }

            isSetup = true
        } else {
            Log.e(TAG, "Plugin already initialized")
        }
        callback.invoke(Result.success(Unit))
    }

    override fun isSetUp(): Boolean = isSetup

    override fun startCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "startCall ${metadata.callId}.")
        PhoneConnectionService.startOutgoingCall(activity, metadata)
        flutterDelegate.setOutgoingCallback(callback)
    }

    override fun reportNewIncomingCall(
        metadata: CallMetadata, callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        Log.i(TAG, "reportNewIncomingCall ${metadata.callId}.")
        // User press hangup or decline call
        if (PhoneConnectionService.connectionManager.isConnectionDisconnected(metadata.callId)) {
            callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED)))
        } else if (PhoneConnectionService.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            if (PhoneConnectionService.connectionManager.isConnectionAnswered(metadata.callId)) {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED)))
            } else {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS)))
            }
        } else {
            PhoneConnectionService.startIncomingCall(activity, metadata)
            callback.invoke(Result.success(null))
        }
    }

    /**
     * Handles the event when a signaling event for call acceptance is received.
     * This method starts establishing a call using the provided call metadata.
     *
     * @param metadata The metadata associated with the call.
     * @param callback A callback function to be invoked after the call acceptance processing is complete.
     *                 It takes a Result object as a parameter, indicating the success or failure of the operation.
     */
    override fun reportConnectedOutgoingCall(
        metadata: CallMetadata, callback: (Result<Unit>) -> Unit
    ) {
        Log.i(TAG, "reportConnectedOutgoingCall ${metadata.callId}.")
        PhoneConnectionService.startEstablishCall(activity, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun reportUpdateCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        Log.i(TAG, "reportUpdateCall ${metadata.callId}.")
        PhoneConnectionService.startUpdateCall(activity, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun reportEndCall(
        metadata: CallMetadata, reason: PEndCallReason, callback: (Result<Unit>) -> Unit
    ) {
        Log.i(TAG, "reportEndCall ${metadata.callId}.")
        PhoneConnectionService.startDeclineCall(activity, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(
        metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        if (PhoneConnectionService.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            Log.i(TAG, "answerCall ${metadata.callId}.")
            PhoneConnectionService.startAnswerCall(activity, metadata)
            callback.invoke(Result.success(null))
        } else {
            Log.e(
                TAG, "Error response as there is no connection with such ${metadata.callId} in the list."
            )
            callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
        }
    }

    override fun endCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "endCall ${metadata.callId}.")
        PhoneConnectionService.startHungUpCall(activity, metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "sendDTMF ${metadata.callId}.")
        PhoneConnectionService.startSendDtmfCall(activity, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "setMuted ${metadata.callId}.")
        PhoneConnectionService.startMutingCall(activity, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "setHeld ${metadata.callId}.")
        PhoneConnectionService.startHoldingCall(activity, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        Log.i(TAG, "setSpeaker ${metadata.callId}.")
        PhoneConnectionService.startSpeaker(activity, metadata)
        callback.invoke(Result.success(null))
    }

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.tearDown(activity)
        incomingCallReceiver.unregisterReceiver()
        callback.invoke(Result.success(Unit))
    }

    override fun detachActivity() {
        Log.i(TAG, "detachActivity")
        incomingCallReceiver.unregisterReceiver()

        try {
            flutterDelegate.clearOutgoingCallback()
            activity.unregisterReceiver(flutterDelegate)
            PhoneConnectionService.notifyAboutDetachActivity(activity)

        } catch (throwable: Throwable) {
            Log.e(TAG, throwable.toString())
        }
    }

    companion object {
        private const val TAG = "TelephonyForegroundCallkeepApi"

        fun notifyAudioRouteChanged(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.ConnectionHasSpeaker.action, metadata.toBundle())
        }

        fun notifyOutgoingCall(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.OngoingCall.action, metadata.toBundle())
        }

        fun notifyAboutDTMF(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.SentDTMF.action, metadata.toBundle())
        }

        fun notifyDeclineCall(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.DeclineCall.action, metadata.toBundle())
        }

        fun notifyAboutHolding(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.ConnectionHolding.action, metadata.toBundle())
        }

        fun notifyAnswer(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.AnswerCall.action, metadata.toBundle())
        }

        fun notifyMuting(context: Context, metadata: CallMetadata) {
            Broadcast.notify(context, ReportAction.AudioMuting.action, metadata.toBundle())
        }

        fun notifyOutgoingFailure(context: Context, failure: FailureMetadata) {
            Broadcast.notify(
                context, FailureAction.OutgoingFailure.action, null, failure.toBundle()
            )
        }

        fun notifyIncomingFailure(context: Context, failure: FailureMetadata) {
            Broadcast.notify(
                context, FailureAction.IncomingFailure.action, null, failure.toBundle()
            )
        }
    }
}
