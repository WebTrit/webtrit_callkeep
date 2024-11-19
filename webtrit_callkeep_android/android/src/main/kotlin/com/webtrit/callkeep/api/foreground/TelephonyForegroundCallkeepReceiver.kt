package com.webtrit.callkeep.api.foreground

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PCallRequestErrorEnum
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.common.PigeonCallback
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.models.toPHandle

/**
 * This class serves as a BroadcastReceiver for handling telephony-related events in the foreground of the application.
 * It is responsible for listening to specific broadcast actions and notifying the Flutter API of various call-related events.
 *
 * @param activity The current Android activity.
 * @param flutterDelegateApi The Flutter API delegate for communication with the Flutter application.
 */
class TelephonyForegroundCallkeepReceiver(
    val activity: Activity,
    private val flutterDelegateApi: PDelegateFlutterApi
) : BroadcastReceiver() {

    private var outgoingCallback: PigeonCallback<PCallRequestError>? = null
    private var isReceiverRegistered = false

    /**
     * Registers this receiver with the provided Android context.
     *
     * @param context The Android context in which to register the receiver.
     */
    fun registerReceiver(context: Context) {
        if (!isReceiverRegistered) {
            Log.i(TAG, "register receiver")

            val intentFilter = createIntentFilter()
            context.registerCustomReceiver(this, intentFilter)
            isReceiverRegistered = true
        } else {
            Log.i(TAG, "skipped receiver already registered")
        }

    }

    private fun createIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(ReportAction.DeclineCall.action)
        intentFilter.addAction(ReportAction.AnswerCall.action)
        intentFilter.addAction(ReportAction.OngoingCall.action)
        intentFilter.addAction(ReportAction.AudioMuting.action)
        intentFilter.addAction(ReportAction.ConnectionHolding.action)
        intentFilter.addAction(ReportAction.SentDTMF.action)
        intentFilter.addAction(ReportAction.ConnectionHasSpeaker.action)
        intentFilter.addAction(ReportAction.DidPushIncomingCall.action)
        intentFilter.addAction(FailureAction.IncomingFailure.action)
        intentFilter.addAction(FailureAction.OutgoingFailure.action)

        Log.i(TAG, "Create registration of actions: ${intentFilter.actionsIterator().asSequence().toList()}")

        return intentFilter
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "onReceive $intent.")

        when (intent?.action) {
            ReportAction.DidPushIncomingCall.action -> handleDidPushIncomingCall(intent.extras)
            ReportAction.DeclineCall.action -> handleDeclineCall(intent.extras)
            ReportAction.AnswerCall.action -> handleAnswerCall(intent.extras)
            ReportAction.OngoingCall.action -> handleOngoingCall(intent.extras)
            ReportAction.ConnectionHasSpeaker.action -> handleConnectionHasSpeaker(intent.extras)
            ReportAction.AudioMuting.action -> handleAudioMuting(intent.extras)
            ReportAction.ConnectionHolding.action -> handleConnectionHolding(intent.extras)
            ReportAction.SentDTMF.action -> handleSentDTMF(intent.extras)
            FailureAction.OutgoingFailure.action -> handleOutgoingFailure(intent.extras)
        }
    }

    fun handleDidPushIncomingCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            flutterDelegateApi.didPushIncomingCall(
                handleArg = metadata.handle!!.toPHandle(),
                displayNameArg = metadata.displayName,
                videoArg = metadata.hasVideo,
                callIdArg = metadata.callId,
                errorArg = null
            ) {}
        }
    }

    private fun handleDeclineCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performEndCall(callMetaData.callId) {}
            flutterDelegateApi.didDeactivateAudioSession {}

            if (Platform.isLockScreen(activity)) {
                activity.finish()
            }
        }
    }

    private fun handleAnswerCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performAnswerCall(callMetaData.callId) {}
            flutterDelegateApi.didActivateAudioSession {}
        }
    }

    private fun handleOngoingCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            outgoingCallback?.invoke(Result.success(null))
            outgoingCallback = null
            flutterDelegateApi.performStartCall(
                callMetaData.callId,
                callMetaData.handle!!.toPHandle(),
                callMetaData.name,
                callMetaData.hasVideo,
            ) {}
        }
    }

    private fun handleConnectionHasSpeaker(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performSetSpeaker(
                callMetaData.callId, callMetaData.hasSpeaker
            ) {}
        }
    }

    private fun handleAudioMuting(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performSetMuted(
                callMetaData.callId, callMetaData.hasMute
            ) {}
        }
    }

    private fun handleConnectionHolding(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performSetHeld(
                callMetaData.callId, callMetaData.hasHold
            ) {}
        }
    }

    private fun handleSentDTMF(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi.performSendDTMF(
                callMetaData.callId, callMetaData.dualToneMultiFrequency.toString()
            ) {}
        }
    }

    private fun handleOutgoingFailure(extras: Bundle?) {
        extras?.let {
            val failureMetaData = FailureMetadata.fromBundle(it)

            outgoingCallback = when (failureMetaData.outgoingFailureType) {
                OutgoingFailureType.UNENTITLED -> {
                    outgoingCallback?.invoke(Result.failure(failureMetaData.getThrowable()))
                    null
                }

                OutgoingFailureType.EMERGENCY_NUMBER -> {
                    outgoingCallback?.invoke(
                        Result.success(
                            PCallRequestError(
                                PCallRequestErrorEnum.EMERGENCY_NUMBER
                            )
                        )
                    )
                    null
                }
            }
        }
    }

    /**
     * Sets the outgoing callback for handling outgoing call request errors.
     *
     * @param callback The callback for outgoing call request errors.
     */
    fun setOutgoingCallback(callback: PigeonCallback<PCallRequestError>?) {
        this.outgoingCallback = callback
    }

    /**
     * Clears the outgoing callback.
     */
    fun clearOutgoingCallback() {
        this.outgoingCallback = null
    }


    companion object {
        private const val TAG = "TelephonyForegroundCallkeepReceiver"
    }
}
