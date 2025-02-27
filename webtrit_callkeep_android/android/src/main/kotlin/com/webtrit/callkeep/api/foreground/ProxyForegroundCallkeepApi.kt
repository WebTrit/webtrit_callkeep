package com.webtrit.callkeep.api.foreground

import android.app.Activity
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PEndCallReasonEnum
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toPHandle
import com.webtrit.callkeep.managers.NotificationManager
import com.webtrit.callkeep.managers.AudioManager

class ProxyForegroundCallkeepApi(
    private val activity: Activity, private val flutterDelegateApi: PDelegateFlutterApi
) : ForegroundCallkeepApi {
    private var isSetup = false
    private val notificationManager = NotificationManager(activity)
    private val audioManager = AudioManager(activity)

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        if (!isSetup) {
            StorageDelegate.initIncomingPath(activity, options.android.incomingPath)
            StorageDelegate.initRootPath(activity, options.android.rootPath)
            StorageDelegate.initRingtonePath(activity, options.android.ringtoneSound)
            StorageDelegate.initRingbackPath(activity, options.android.ringbackSound)
            isSetup = true
        } else {
            Log.i(LOG_TAG, "Plugin already initialized")
        }
        callback.invoke(Result.success(Unit))
    }

    override fun startCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        callback.invoke(Result.success(null))
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
        notificationManager.showIncomingCallNotification(metadata, hasAnswerButton = false)
        this@ProxyForegroundCallkeepApi.audioManager.startRingtone(metadata.ringtonePath)
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
        notificationManager.cancelActiveCallNotification(metadata.callId)
        this@ProxyForegroundCallkeepApi.audioManager.stopRingtone()
        if (Platform.isLockScreen(activity)) {
            ActivityHolder.finish();
        }
        if (reason.value == PEndCallReasonEnum.UNANSWERED) {
            notificationManager.showMissedCallNotification(metadata)
        }
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(
        metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        flutterDelegateApi.performAnswerCall(metadata.callId) {}
        flutterDelegateApi.didActivateAudioSession {}
        audioManager.stopRingtone()
        callback.invoke(Result.success(null))
    }

    override fun endCall(metadata: CallMetadata, callback: (Result<PCallRequestError?>) -> Unit) {
        flutterDelegateApi.performEndCall(metadata.callId) {}
        flutterDelegateApi.didDeactivateAudioSession {}

        notificationManager.cancelActiveCallNotification(metadata.callId)
        this@ProxyForegroundCallkeepApi.audioManager.stopRingtone()
        if (Platform.isLockScreen(activity)) {
            ActivityHolder.finish();
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

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        callback.invoke(Result.success(Unit))
    }

    override fun detachActivity() {}

    companion object {
        private const val LOG_TAG = "ProxyForegroundCallkeepApi"
    }
}
