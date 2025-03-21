package com.webtrit.callkeep.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostApi
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.foreground.ForegroundCallkeepApi
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.toCallHandle

class ForegroundService : Service(), PHostApi {
    private val binder = LocalBinder()

    private lateinit var foregroundCallkeepApi: ForegroundCallkeepApi;

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
            foregroundCallkeepApi = CallkeepApiProvider.getForegroundCallkeepApi(baseContext, flutterDelegateApi!!)
        }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        foregroundCallkeepApi.setUp(options, callback)
    }

    @SuppressLint("MissingPermission")
    override fun startCall(
        callId: String,
        handle: PHandle,
        displayNameOrContactIdentifier: String?,
        video: Boolean,
        proximityEnabled: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video,
            proximityEnabled = proximityEnabled,
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
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(baseContext)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath
        )
        foregroundCallkeepApi.reportNewIncomingCall(callMetaData, callback)
    }

    override fun isSetUp(): Boolean = foregroundCallkeepApi.isSetUp()

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        foregroundCallkeepApi.tearDown(callback)
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
        proximityEnabled: Boolean?,
        callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle?.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo == true,
            proximityEnabled = proximityEnabled == true,
        )
        foregroundCallkeepApi.reportUpdateCall(callMetaData, callback)
    }

    override fun reportEndCall(
        callId: String, displayName: String, reason: PEndCallReason, callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            displayName = displayName
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
        foregroundCallkeepApi.endCall(callMetaData, callback)
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            dualToneMultiFrequency = key.getOrNull(0),
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
        foregroundCallkeepApi.setSpeaker(callMetaData, callback)
    }


    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val TAG = "MyBoundService"
    }
}