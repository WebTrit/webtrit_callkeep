package com.webtrit.callkeep

import android.annotation.SuppressLint
import android.app.Activity

import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.api.foreground.ForegroundCallkeepApi
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.common.models.CallPaths
import com.webtrit.callkeep.common.models.toCallHandle

class PigeonActivityApi(
    private val activity: Activity, flutterDelegateApi: PDelegateFlutterApi,
) : PHostApi {
    private val foregroundCallkeepApi: ForegroundCallkeepApi =
        CallkeepApiProvider.getForegroundCallkeepApi(activity, flutterDelegateApi)

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        foregroundCallkeepApi.setUp(options, callback)
    }

    @SuppressLint("MissingPermission")
    override fun startCall(
        callId: String,
        handle: PHandle,
        displayNameOrContactIdentifier: String?,
        video: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video
        )

        foregroundCallkeepApi.startCall(callMetaData, callback)
    }

    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        val callPath = StorageDelegate.getIncomingPath(activity)
        val rootPath = StorageDelegate.getRootPath(activity)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            paths = CallPaths(callPath, rootPath)
        )

        foregroundCallkeepApi.reportNewIncomingCall(callMetaData, callback)
    }

    override fun isSetUp(): Boolean = foregroundCallkeepApi.isSetUp()

    // Only for iOS, not used in Android
    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        callback.invoke(Result.success(Unit))
    }

    // Only for iOS, not used in Android
    override fun reportConnectingOutgoingCall(
        callId: String, callback: (Result<Unit>) -> Unit
    ) {
        callback.invoke(Result.success(Unit))
    }

    override fun reportConnectedOutgoingCall(
        callId: String, callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
        )
        foregroundCallkeepApi.reportConnectedOutgoingCall(callMetaData, callback)
    }

    override fun reportUpdateCall(
        callId: String,
        handle: PHandle?,
        displayName: String?,
        hasVideo: Boolean?,
        callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle?.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo
        )
        foregroundCallkeepApi.reportUpdateCall(callMetaData, callback)
    }

    override fun reportEndCall(
        callId: String, reason: PEndCallReason, callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId
        )
        foregroundCallkeepApi.reportEndCall(callMetaData, reason, callback)
    }

    override fun answerCall(
        callId: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId
        )
        foregroundCallkeepApi.answerCall(callMetaData, callback)
    }

    override fun endCall(
        callId: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId
        )
        foregroundCallkeepApi.endCall(callMetaData, callback);
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            dualToneMultiFrequency = key,
        )
        foregroundCallkeepApi.sendDTMF(callMetaData, callback)
    }

    override fun setMuted(
        callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            hasMute = muted,
        )
        foregroundCallkeepApi.setMuted(callMetaData, callback)
    }

    override fun setHeld(
        callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            hasHold = onHold,
        )
        foregroundCallkeepApi.setHeld(callMetaData, callback)
    }

    override fun setSpeaker(
        callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            hasSpeaker = enabled,
        )
        foregroundCallkeepApi.setSpeaker(callMetaData, callback);
    }

    fun detachActivity() {
        foregroundCallkeepApi.detachActivity()
    }
}
