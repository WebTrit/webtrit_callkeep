package com.webtrit.callkeep.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PCallRequestErrorEnum
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostApi
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.models.ConnectionReport
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.PigeonCallback
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.helpers.Platform
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.models.toPHandle
import com.webtrit.callkeep.notifications.NotificationChannelManager
import com.webtrit.callkeep.services.dispatchers.EventDispatcher
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

@Keep
class ForegroundService : Service(), PHostApi {
    private val binder = LocalBinder()

    private var outgoingCallback: PigeonCallback<PCallRequestError>? = null

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
        }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        EventDispatcher.registerService(this::class.java)
        isRunning = true
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand ${intent?.action}")

        when (intent?.action) {
            ConnectionReport.DidPushIncomingCall.name -> handleDidPushIncomingCall(intent.extras)
            ConnectionReport.DeclineCall.name -> handleDeclineCall(intent.extras)
            ConnectionReport.AnswerCall.name -> handleAnswerCall(intent.extras)
            ConnectionReport.OngoingCall.name -> handleOngoingCall(intent.extras)
            ConnectionReport.ConnectionHasSpeaker.name -> handleConnectionHasSpeaker(intent.extras)
            ConnectionReport.AudioMuting.name -> handleAudioMuting(intent.extras)
            ConnectionReport.ConnectionHolding.name -> handleConnectionHolding(intent.extras)
            ConnectionReport.SentDTMF.name -> handleSentDTMF(intent.extras)
            ConnectionReport.OutgoingFailure.name -> handleOutgoingFailure(intent.extras)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        // Registers all necessary notification channels for the application.
        // This includes channels for active calls, incoming calls, missed calls, and foreground calls.
        NotificationChannelManager.registerNotificationChannels(baseContext)

        Telecom.registerPhoneAccount(baseContext)
        StorageDelegate.Sound.initRingtonePath(baseContext, options.android.ringtoneSound)
        StorageDelegate.Sound.initRingbackPath(baseContext, options.android.ringbackSound)

        callback.invoke(Result.success(Unit))

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
        Log.i(TAG, "startCall $callId.")
        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video,
            proximityEnabled = proximityEnabled,
        )
        PhoneConnectionService.Companion.startOutgoingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(baseContext)

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath
        )

        if (PhoneConnectionService.Companion.connectionManager.isConnectionDisconnected(metadata.callId)) {
            callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED)))
        } else if (PhoneConnectionService.Companion.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            if (PhoneConnectionService.Companion.connectionManager.isConnectionAnswered(metadata.callId)) {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED)))
            } else {
                callback.invoke(Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS)))
            }
        } else {
            PhoneConnectionService.Companion.startIncomingCall(baseContext, metadata)
            callback.invoke(Result.success(null))
        }

        callback.invoke(Result.success(null))
    }

    override fun isSetUp(): Boolean = true

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.Companion.tearDown(baseContext)
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
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.Companion.startEstablishCall(baseContext, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun reportUpdateCall(
        callId: String,
        handle: PHandle?,
        displayName: String?,
        hasVideo: Boolean?,
        proximityEnabled: Boolean?,
        callback: (Result<Unit>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            handle = handle?.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo == true,
            proximityEnabled = proximityEnabled == true,
        )
        PhoneConnectionService.Companion.startUpdateCall(baseContext, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun reportEndCall(
        callId: String, displayName: String, reason: PEndCallReason, callback: (Result<Unit>) -> Unit
    ) {
        Log.i(TAG, "reportEndCall $callId.")
        val callMetaData = CallMetadata(
            callId = callId, displayName = displayName
        )
        PhoneConnectionService.Companion.startDeclineCall(baseContext, callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(
        callId: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId
        )
        if (PhoneConnectionService.Companion.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            Log.i(TAG, "answerCall ${metadata.callId}.")
            PhoneConnectionService.Companion.startAnswerCall(baseContext, metadata)
            callback.invoke(Result.success(null))
        } else {
            Log.e(
                TAG, "Error response as there is no connection with such ${metadata.callId} in the list."
            )
            callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
        }
    }

    override fun endCall(
        callId: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        Log.i(TAG, "endCall $callId.")
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.Companion.startHungUpCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            dualToneMultiFrequency = key.getOrNull(0),
        )
        PhoneConnectionService.Companion.startSendDtmfCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(
        callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            hasMute = muted,
        )
        PhoneConnectionService.Companion.startMutingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(
        callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            hasHold = onHold,
        )
        PhoneConnectionService.Companion.startHoldingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            hasSpeaker = enabled,
        )
        PhoneConnectionService.Companion.startSpeaker(baseContext, metadata)
        callback.invoke(Result.success(null))
    }


    fun handleDidPushIncomingCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            flutterDelegateApi?.didPushIncomingCall(
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
            flutterDelegateApi?.performEndCall(callMetaData.callId) {}
            flutterDelegateApi?.didDeactivateAudioSession {}

            if (Platform.isLockScreen(baseContext)) {
                ActivityHolder.finish()
            }
        }
    }

    private fun handleAnswerCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAnswerCall(callMetaData.callId) {}
            flutterDelegateApi?.didActivateAudioSession {}
        }
    }

    private fun handleOngoingCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            outgoingCallback?.invoke(Result.success(null))
            outgoingCallback = null
            flutterDelegateApi?.performStartCall(
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
            flutterDelegateApi?.performSetSpeaker(
                callMetaData.callId, callMetaData.hasSpeaker
            ) {}
        }
    }

    private fun handleAudioMuting(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetMuted(
                callMetaData.callId, callMetaData.hasMute
            ) {}
        }
    }

    private fun handleConnectionHolding(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetHeld(
                callMetaData.callId, callMetaData.hasHold
            ) {}
        }
    }

    private fun handleSentDTMF(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSendDTMF(
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

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        isRunning = false
        EventDispatcher.unregisterService(this::class.java)
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val TAG = "ForegroundService"

        var isRunning = false
    }
}