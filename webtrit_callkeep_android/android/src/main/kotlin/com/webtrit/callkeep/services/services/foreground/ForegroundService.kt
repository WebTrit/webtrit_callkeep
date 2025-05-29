package com.webtrit.callkeep.services.services.foreground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.Keep
import com.webtrit.callkeep.PCallRequestError
import com.webtrit.callkeep.PCallRequestErrorEnum
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostApi
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.POptions
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.PigeonCallback
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.models.toPHandle
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService

/**
 * ForegroundService is an Android bound Service that maintains a connection with the main Flutter isolate
 * while the app's activity is active. It implements the [com.webtrit.callkeep.PHostApi] interface to receive and handle method calls
 * from the Flutter side via Pigeon.
 *
 * Responsibilities:
 * - Acts as a bridge between Android Telecom API and Flutter.
 * - Handles both incoming and outgoing call actions.
 * - Sends updates back to Flutter using [com.webtrit.callkeep.PDelegateFlutterApi].
 * - Manages call features such as mute, hold, speaker, DTMF.
 * - Registers notification channels and Telecom PhoneAccount on setup.
 * - Listens for ConnectionService reports via intents.
 *
 * Lifecycle:
 * - Bound to the activity lifecycle: starts when activity is active, stops when unbound.
 * - Registers and unregisters itself with [ConnectionServicePerformBroadcaster] for communication.
 */
@Keep
class ForegroundService : Service(), PHostApi {
    private val binder = LocalBinder()

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var outgoingCallback: PigeonCallback<PCallRequestError>? = null

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
        }

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectionPerform.DidPushIncomingCall.name -> handleCSReportDidPushIncomingCall(intent.extras)
                ConnectionPerform.DeclineCall.name -> handleCSReportDeclineCall(intent.extras)
                ConnectionPerform.HungUp.name -> handleCSReportDeclineCall(intent.extras)
                ConnectionPerform.AnswerCall.name -> handleCSReportAnswerCall(intent.extras)
                ConnectionPerform.OngoingCall.name -> handleCSReportOngoingCall(intent.extras)
                ConnectionPerform.ConnectionHasSpeaker.name -> handleCSReportConnectionHasSpeaker(intent.extras)
                ConnectionPerform.AudioMuting.name -> handleCSReportAudioMuting(intent.extras)
                ConnectionPerform.ConnectionHolding.name -> handleCSReportConnectionHolding(intent.extras)
                ConnectionPerform.SentDTMF.name -> handleCSReportSentDTMF(intent.extras)
                ConnectionPerform.OutgoingFailure.name -> handleCSReportOutgoingFailure(intent.extras)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        // Register the service to receive connection service perform events
        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            ConnectionPerform.entries, baseContext, connectionServicePerformReceiver
        )
        isRunning = true
    }

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        // Registers all necessary notification channels for the application.
        // This includes channels for active calls, incoming calls, missed calls, and foreground calls.
        NotificationChannelManager.registerNotificationChannels(baseContext)

        TelephonyUtils(baseContext).registerPhoneAccount()

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
        Log.i(TAG, "startCall $callId")

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video,
            proximityEnabled = proximityEnabled
        )

        // Store the callback to be used later (for success, timeout, or failure in connection services)
        outgoingCallback = callback

        // Create a timeout handler in case the system does not respond in time
        val timeoutRunnable = Runnable {
            outgoingCallback?.let {
                Log.w(TAG, "startCall timeout for $callId")
                it(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
                outgoingCallback = null
            }
        }

        // Schedule the timeout runnable to run after a delay
        handler.postDelayed(timeoutRunnable, CALLBACK_TIMEOUT_MS)

        try {
            PhoneConnectionService.startOutgoingCall(baseContext, metadata)
        } catch (_: EmergencyNumberException) {
            // Handle case where the number is recognized as an emergency number
            handler.removeCallbacks(timeoutRunnable)
            outgoingCallback?.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER)))
            outgoingCallback = null
        } catch (e: Exception) {
            // Handle any other unexpected exceptions
            handler.removeCallbacks(timeoutRunnable)
            Log.e(TAG, "startCall failed: ${e.message}")
            outgoingCallback?.invoke(Result.failure(e))
            outgoingCallback = null
        }
    }

    // TODO: Move logic to the PhoneConnectionService
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

        PhoneConnectionService.Companion.startIncomingCall(
            context = baseContext,
            metadata = metadata,
            onSuccess = { callback(Result.success(null)) },
            onError = { error -> callback(Result.success(error)) })
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

    override fun reportConnectedOutgoingCall(callId: String, callback: (Result<Unit>) -> Unit) {
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
        val callMetaData = CallMetadata(callId = callId, displayName = displayName)
        PhoneConnectionService.Companion.startDeclineCall(baseContext, callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId)
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

    override fun endCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        Log.i(TAG, "endCall $callId.")
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.Companion.startHungUpCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId, dualToneMultiFrequency = key.getOrNull(0))
        PhoneConnectionService.Companion.startSendDtmfCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId, hasMute = muted)
        PhoneConnectionService.Companion.startMutingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId, hasHold = onHold)
        PhoneConnectionService.Companion.startHoldingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId, hasSpeaker = enabled)
        PhoneConnectionService.Companion.startSpeaker(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    // --------------------------------
    // Handlers for ConnectionService reports to communicate with the Flutter side
    // --------------------------------
    //

    private fun handleCSReportDidPushIncomingCall(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.didPushIncomingCall(
                handleArg = metadata.handle!!.toPHandle(),
                displayNameArg = metadata.displayName,
                videoArg = metadata.hasVideo,
                callIdArg = metadata.callId,
                errorArg = null
            ) {}
        }
    }

    private fun handleCSReportDeclineCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performEndCall(callMetaData.callId) {}
            flutterDelegateApi?.didDeactivateAudioSession {}

            if (Platform.isLockScreen(baseContext)) {
                ActivityHolder.finish()
            }
        }
    }

    private fun handleCSReportAnswerCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performAnswerCall(callMetaData.callId) {}
            flutterDelegateApi?.didActivateAudioSession {}
        }
    }

    private fun handleCSReportOngoingCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
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

    private fun handleCSReportConnectionHasSpeaker(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performSetSpeaker(
                callMetaData.callId, callMetaData.hasSpeaker
            ) {}
        }
    }

    private fun handleCSReportAudioMuting(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performSetMuted(
                callMetaData.callId, callMetaData.hasMute
            ) {}
        }
    }

    private fun handleCSReportConnectionHolding(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performSetHeld(
                callMetaData.callId, callMetaData.hasHold
            ) {}
        }
    }

    private fun handleCSReportSentDTMF(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.Companion.fromBundle(it)
            flutterDelegateApi?.performSendDTMF(
                callMetaData.callId, callMetaData.dualToneMultiFrequency.toString()
            ) {}
        }
    }

    private fun handleCSReportOutgoingFailure(extras: Bundle?) {
        extras?.let {
            val failureMetaData = FailureMetadata.Companion.fromBundle(it)
            Log.e(TAG, "handleCSReportOutgoingFailure: ${failureMetaData.outgoingFailureType}")
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

    //
    // --------------------------------
    // Handlers for ConnectionService reports to communicate with the Flutter side
    // --------------------------------

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind")
        stopSelf()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the service from receiving connection service perform events
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(
            baseContext, connectionServicePerformReceiver
        )
        isRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val TAG = "ForegroundService"
        const val CALLBACK_TIMEOUT_MS = 5000L

        var isRunning = false
    }
}