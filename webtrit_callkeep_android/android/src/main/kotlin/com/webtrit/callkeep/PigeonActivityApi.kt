package com.webtrit.callkeep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.ActivityCompat.requestPermissions
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.foreground.ForegroundCallkeepApi
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.common.models.CallPaths
import com.webtrit.callkeep.common.models.NotificationAction
import com.webtrit.callkeep.common.models.toCallHandle

class PigeonActivityApi(
    private val activity: Activity, flutterDelegateApi: PDelegateFlutterApi
) : PHostApi, BroadcastReceiver() {
    private val foregroundCallkeepApi: ForegroundCallkeepApi =
        CallkeepApiProvider.getForegroundCallkeepApi(activity, flutterDelegateApi)


    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        foregroundCallkeepApi.setUp(options, callback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Register actions from notification
        val notificationsReceiverFilter = IntentFilter()
        notificationsReceiverFilter.addAction(NotificationAction.Hangup.action)
        notificationsReceiverFilter.addAction(NotificationAction.Answer.action)
        activity.registerCustomReceiver(this, notificationsReceiverFilter)
        
        StorageDelegate.setActivityReady(activity, true);
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
        val callPath = StorageDelegate.getIncomingPath(activity)
        val rootPath = StorageDelegate.getRootPath(activity)
        val ringtonePath = StorageDelegate.getRingtonePath(activity)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            paths = CallPaths(callPath, rootPath),
            ringtonePath = ringtonePath
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
        proximityEnabled: Boolean?,
        callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle?.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            proximityEnabled = proximityEnabled,
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
        activity.unregisterReceiver(this)

        StorageDelegate.setActivityReady(activity, false);
    }

    // Receiver for notification actions when app is visible
    override fun onReceive(context: Context, intent: Intent) {
        val callMetaData = CallMetadata.fromBundle(intent.extras!!)

        when (intent.action) {
            NotificationAction.Hangup.action -> {
                foregroundCallkeepApi.endCall(callMetaData) {}
            }

            NotificationAction.Answer.action -> {
                foregroundCallkeepApi.answerCall(callMetaData) {}
            }
        }
    }
}
