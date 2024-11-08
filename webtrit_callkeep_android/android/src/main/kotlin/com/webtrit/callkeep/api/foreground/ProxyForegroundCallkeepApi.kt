package com.webtrit.callkeep.api.foreground

import android.app.Activity
import com.webtrit.callkeep.FlutterLog

import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PEndCallReasonEnum
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.common.models.toPHandle
import com.webtrit.callkeep.services.NotificationService
import com.webtrit.callkeep.services.AudioService

class ProxyForegroundCallkeepApi(
    private val activity: Activity, private val flutterDelegateApi: PDelegateFlutterApi
) : ForegroundCallkeepApi {
    private var isSetup = false
    private val notificationService = NotificationService(activity)
    private val audioService = AudioService(activity)


    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        if (!isSetup) {
            StorageDelegate.initIncomingPath(activity, options.android.incomingPath)
            StorageDelegate.initRootPath(activity, options.android.rootPath)
            StorageDelegate.initRingtonePath(activity, options.android.ringtoneSound)
            isSetup = true
        } else {
            FlutterLog.e(LOG_TAG, "Plugin already initialized")
        }
        callback.invoke(Result.success(Unit))
    }

    override fun startCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        callback.invoke(Result.Companion.success(null))
        flutterDelegateApi.performStartCall(
            metadata.callId,
            metadata.handle!!.toPHandle(),
            metadata.name,
            metadata.hasVideo,
        ) {}

    }

    override fun reportNewIncomingCall(
        metadata: CallMetadata, callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        notificationService.showIncomingCallNotification(metadata, hasAnswerButton = false)
        audioService.startRingtone(metadata.ringtonePath)
        callback.invoke(Result.success(null))
    }

    override fun isSetUp(): Boolean = isSetup

    override fun reportConnectedOutgoingCall(
        metadata: CallMetadata, callback: (Result<Unit>) -> Unit
    ) {
        flutterDelegateApi.performAnswerCall(metadata.callId) {}
        callback.invoke(Result.success(Unit))
    }

    override fun reportUpdateCall(metadata: CallMetadata, callback: (Result<Unit>) -> Unit) {
        callback.invoke(Result.success(Unit))
    }

    override fun reportEndCall(
        metadata: CallMetadata, reason: PEndCallReason, callback: (Result<Unit>) -> Unit
    ) {
        flutterDelegateApi.performEndCall(metadata.callId) {}
        flutterDelegateApi.didDeactivateAudioSession {}
        notificationService.cancelActiveNotification()
        audioService.stopRingtone()
        if (Platform.isLockScreen(activity)) {
            activity.finish()
        }
        if (reason.value == PEndCallReasonEnum.UNANSWERED) {
            notificationService.showMissedCallNotification(metadata)
        }
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(
        metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        flutterDelegateApi.performAnswerCall(metadata.callId) {}
        flutterDelegateApi.didActivateAudioSession {}
        callback.invoke(Result.success(null))
    }

    override fun endCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        flutterDelegateApi.performEndCall(metadata.callId) {}
        flutterDelegateApi.didDeactivateAudioSession {}

        notificationService.cancelActiveNotification()
        audioService.stopRingtone()
        if (Platform.isLockScreen(activity)) {
            activity.finish()
        }
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        flutterDelegateApi.performSendDTMF(
            metadata.callId, metadata.dualToneMultiFrequency.toString()
        ) {}
        callback.invoke(Result.success(null))
    }

    override fun setMuted(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        flutterDelegateApi.performSetMuted(metadata.callId, metadata.hasMute) {}
        callback.invoke(Result.success(null))
    }

    override fun setHeld(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        flutterDelegateApi.performSetHeld(
            metadata.callId, metadata.hasHold
        ) {}
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        flutterDelegateApi.performSetSpeaker(
            metadata.callId, metadata.hasSpeaker
        ) {}
        callback.invoke(Result.success(null))
    }

    override fun detachActivity() {}

    companion object {
        private const val LOG_TAG = "ProxyForegroundCallkeepApi"
    }
}
