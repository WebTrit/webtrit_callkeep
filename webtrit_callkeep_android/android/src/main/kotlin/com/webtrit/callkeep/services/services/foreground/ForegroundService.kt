package com.webtrit.callkeep.services.services.foreground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.Keep
import com.webtrit.callkeep.PAudioDevice
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
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.common.RetryConfig
import com.webtrit.callkeep.common.RetryDecider
import com.webtrit.callkeep.common.RetryManager
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.common.isCallPhoneSecurityException
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.models.toAudioDevice
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.models.toPAudioDevice
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
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Manages all pending outgoing call callbacks and their associated timeouts.
     *
     * This manager is required because communication between the [ForegroundService]
     * and [PhoneConnectionService] happens asynchronously through broadcast receivers.
     * When an outgoing call is initiated, the request and its response (success, failure,
     * or timeout) may occur at different times, often triggered by asynchronous intents
     * from the connection service layer.
     *
     * Responsibilities:
     * - Tracks active outgoing call requests identified by `callId`.
     * - Stores the callback to be invoked once the connection service reports a result.
     * - Automatically cancels pending timeouts when a call completes, fails, or times out.
     * - Ensures safe cleanup on service destruction to prevent memory leaks.
     *
     * The manager runs on the main thread using a [Handler] backed by [Looper.getMainLooper],
     * and applies CALLBACK_TIMEOUT_MS as the default timeout duration for each outgoing call.
     */
    private val outgoingCallbacksManager by lazy { OutgoingCallbacksManager(mainHandler) }

    /**
     * Manages retry logic for outgoing call operations that may temporarily fail
     * due to unregistered or inactive self-managed PhoneAccount.
     *
     * This manager is responsible for automatically retrying `startOutgoingCall`
     * when a {@link SecurityException} occurs with a CALL_PHONE permission message —
     * a typical sign that the PhoneAccount is not yet fully registered in Telecom.
     *
     * Behavior:
     * - Uses [CallPhoneSecurityRetryDecider] to decide whether a retry is allowed.
     * - Applies exponential backoff between attempts (configured via [RetryConfig]).
     * - Cancels all pending retries when the call succeeds or fails permanently.
     *
     * The [RetryManager] runs on the main thread via [mainHandler].
     */
    private val retryManager by lazy {
        RetryManager<String>(mainHandler, CallPhoneSecurityRetryDecider)
    }

    private val binder = LocalBinder()

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
        }

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectionPerform.DidPushIncomingCall.name -> handleCSReportDidPushIncomingCall(
                    intent.extras
                )

                ConnectionPerform.DeclineCall.name -> handleCSReportDeclineCall(intent.extras)
                ConnectionPerform.HungUp.name -> handleCSReportDeclineCall(intent.extras)
                ConnectionPerform.AnswerCall.name -> handleCSReportAnswerCall(intent.extras)
                ConnectionPerform.OngoingCall.name -> handleCSReportOngoingCall(intent.extras)
                ConnectionPerform.ConnectionHasSpeaker.name -> handleCSReportConnectionHasSpeaker(
                    intent.extras
                )

                ConnectionPerform.AudioDeviceSet.name -> handleCSReportAudioDeviceSet(intent.extras)
                ConnectionPerform.AudioDevicesUpdate.name -> handleCsReportAudioDevicesUpdate(intent.extras)
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
        logger.i("setUp")

        try {
            TelephonyUtils(baseContext).registerPhoneAccount()
        } catch (e: Exception) {
            callback(Result.failure(e))
            return
        }

        runCatching {
            // Registers all necessary notification channels for the application.
            // This includes channels for active calls, incoming calls, missed calls, and foreground calls.
            NotificationChannelManager.registerNotificationChannels(baseContext)
        }.onFailure { Log.w("CallKeep", "Channel registration failed: ${it.message}", it) }

        runCatching {
            StorageDelegate.Sound.initRingtonePath(baseContext, options.android.ringtoneSound)
            StorageDelegate.Sound.initRingbackPath(baseContext, options.android.ringbackSound)
        }.onFailure { Log.w("CallKeep", "Sound init failed: ${it.message}", it) }

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
        val logContext = "startCall($callId|$handle)"
        logger.i("$logContext: trying to start call")

        // reset any previous pending callback for this call
        outgoingCallbacksManager.remove(callId)

        // register callback + a global timeout for the whole outgoing flow
        outgoingCallbacksManager.put(callId, callback) { cb ->
            logger.w("$logContext: overall timeout reached")
            cb(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
            // ensure retry is stopped
            retryManager.cancel(callId)
        }

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video,
            proximityEnabled = proximityEnabled
        )

        // Kick off retry loop that wraps the "start outgoing call" attempt + PA re-registration on SecurityException(CALL_PHONE)
        retryManager.run(key = callId, config = OUTGOING_RETRY_CONFIG, onAttemptStart = { attempt ->
            logger.i("$logContext attempt $attempt/${OUTGOING_REGISTER_RETRY_MAX}")
            outgoingCallbacksManager.rescheduleTimeout(callId)
        }, onSuccess = {
            // The client callback is not invoked here — it will be triggered
            // asynchronously later from handleCSReportOngoingCall
            // via invokeAndRemove(...).
            logger.i("$logContext: operation succeeded(will be confirmed by CS report)")
        }, onFinalFailure = { err ->
            logger.w("$logContext: give up after retries. reason=${err.javaClass.simpleName}: ${err.message}")
            outgoingCallbacksManager.invokeAndRemove(
                callId, Result.success(
                    when (err) {
                        is EmergencyNumberException -> PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER)
                        is SecurityException if err.isCallPhoneSecurityException() -> PCallRequestError(
                            PCallRequestErrorEnum.SELF_MANAGED_PHONE_ACCOUNT_NOT_REGISTERED
                        )

                        else -> PCallRequestError(PCallRequestErrorEnum.INTERNAL)
                    }
                )
            )
        }) { attempt ->
            // Skip registration on the first attempt since the PhoneAccount is expected
            // to have been registered during the initial setup (setUp()).
            // Re-register the self-managed PhoneAccount only on subsequent attempts
            // in case the initial registration wasn't yet active in the Telecom service.
            if (attempt > 1) TelephonyUtils(baseContext).registerPhoneAccount()

            try {
                PhoneConnectionService.startOutgoingCall(baseContext, metadata)
                // If start succeeded synchronously, just return; success will be confirmed by CS report.
                // We do NOT throw -> RetryManager treats as success and stops scheduling more attempts.
            } catch (e: EmergencyNumberException) {
                logger.e("$logContext failed: emergency number", e)
                throw e
            } catch (e: SecurityException) {
                logger.e("$logContext SecurityException ${e.message}", e)
                // let RetryManager decide: retry only if CALL_PHONE flavor and attempts remain
                throw e
            } catch (e: Exception) {
                logger.e("$logContext failed on attempt $attempt: ${e.message}", e)
                throw e
            }
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

        PhoneConnectionService.startIncomingCall(
            context = baseContext,
            metadata = metadata,
            onSuccess = { callback(Result.success(null)) },
            onError = { error -> callback(Result.success(error)) })
    }

    override fun isSetUp(): Boolean = true

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        PhoneConnectionService.tearDown(baseContext)
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
        PhoneConnectionService.startEstablishCall(baseContext, metadata)
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
        PhoneConnectionService.startUpdateCall(baseContext, metadata)
        callback.invoke(Result.success(Unit))
    }

    override fun reportEndCall(
        callId: String,
        displayName: String,
        reason: PEndCallReason,
        callback: (Result<Unit>) -> Unit
    ) {
        val callMetaData = CallMetadata(callId = callId, displayName = displayName)
        PhoneConnectionService.startDeclineCall(baseContext, callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId)
        if (PhoneConnectionService.connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            logger.i("answerCall ${metadata.callId}.")
            PhoneConnectionService.startAnswerCall(baseContext, metadata)
            callback.invoke(Result.success(null))
        } else {
            logger.e("Error response as there is no connection with such ${metadata.callId} in the list.")
            callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
        }
    }

    override fun endCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        logger.i("endCall $callId.")
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.startHungUpCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(callId = callId, dualToneMultiFrequency = key.getOrNull(0))
        PhoneConnectionService.startSendDtmfCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(
        callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(callId = callId, hasMute = muted)
        PhoneConnectionService.startMutingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(
        callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(callId = callId, hasHold = onHold)
        PhoneConnectionService.startHoldingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(callId = callId, hasSpeaker = enabled)
        PhoneConnectionService.startSpeaker(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setAudioDevice(
        callId: String, device: PAudioDevice, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId, audioDevice = device.toAudioDevice()
        )
        PhoneConnectionService.setAudioDevice(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    // --------------------------------
    // Handlers for ConnectionService reports to communicate with the Flutter side
    // --------------------------------
    //

    private fun handleCSReportDidPushIncomingCall(extras: Bundle?) {
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

    private fun handleCSReportDeclineCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performEndCall(callMetaData.callId) {}
            flutterDelegateApi?.didDeactivateAudioSession {}

            if (Platform.isLockScreen(baseContext)) {
                ActivityHolder.finish()
            }
        }
    }

    private fun handleCSReportAnswerCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAnswerCall(callMetaData.callId) {}
            flutterDelegateApi?.didActivateAudioSession {}
        }
    }

    private fun handleCSReportOngoingCall(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            retryManager.cancel(callMetaData.callId)
            outgoingCallbacksManager.invokeAndRemove(callMetaData.callId, Result.success(null))

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
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetSpeaker(
                callMetaData.callId, callMetaData.hasSpeaker
            ) {}
        }
    }

    private fun handleCSReportAudioDeviceSet(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAudioDeviceSet(
                callMetaData.callId, callMetaData.audioDevice!!.toPAudioDevice()
            ) {}
        }
    }

    private fun handleCsReportAudioDevicesUpdate(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAudioDevicesUpdate(
                callMetaData.callId,
                callMetaData.audioDevices.map { audioDevice -> audioDevice.toPAudioDevice() }) {}
        }
    }

    private fun handleCSReportAudioMuting(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetMuted(
                callMetaData.callId, callMetaData.hasMute
            ) {}
        }
    }

    private fun handleCSReportConnectionHolding(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetHeld(
                callMetaData.callId, callMetaData.hasHold
            ) {}
        }
    }

    private fun handleCSReportSentDTMF(extras: Bundle?) {
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSendDTMF(
                callMetaData.callId, callMetaData.dualToneMultiFrequency.toString()
            ) {}
        }
    }

    private fun handleCSReportOutgoingFailure(extras: Bundle?) {
        extras?.let { failure ->
            val failureMetaData = FailureMetadata.fromBundle(failure)
            logger.e("handleCSReportOutgoingFailure: ${failureMetaData.outgoingFailureType}")

            val callId = failureMetaData.callMetadata?.callId ?: return
            retryManager.cancel(callId)

            val cb = outgoingCallbacksManager.remove(callId)

            when (failureMetaData.outgoingFailureType) {
                OutgoingFailureType.UNENTITLED -> {
                    cb?.invoke(Result.failure(failureMetaData.getThrowable()))
                }

                OutgoingFailureType.EMERGENCY_NUMBER -> {
                    cb?.invoke(
                        Result.success(PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER))
                    )
                }
            }
        }
    }

    //
    // --------------------------------
    // Handlers for ConnectionService reports to communicate with the Flutter side
    // --------------------------------

    override fun onUnbind(intent: Intent?): Boolean {
        logger.i("onUnbind")
        stopSelf()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the service from receiving connection service perform events
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(
            baseContext, connectionServicePerformReceiver
        )

        retryManager.clear()
        outgoingCallbacksManager.clear()

        isRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val TAG = "ForegroundService"

        private val logger = Log(TAG)

        // Maximum number of retries if PhoneAccount is not yet registered
        private const val OUTGOING_REGISTER_RETRY_MAX = 5

        // Delay between retries (milliseconds)
        private const val OUTGOING_REGISTER_RETRY_DELAY_MS = 750L

        // Unified retry configuration for outgoing calls
        val OUTGOING_RETRY_CONFIG = RetryConfig(
            maxAttempts = OUTGOING_REGISTER_RETRY_MAX,
            initialDelayMs = OUTGOING_REGISTER_RETRY_DELAY_MS,
            backoffMultiplier = 1.5,
            maxDelayMs = 5_000L
        )

        var isRunning = false
    }
}

private class OutgoingCallbacksManager(
    private val handler: Handler, private val timeoutMs: Long = CALLBACK_TIMEOUT_MS
) {
    private val callbacks = mutableMapOf<String, (Result<PCallRequestError?>) -> Unit>()
    private val timeouts = mutableMapOf<String, Runnable>()
    private val timeoutHandlers =
        mutableMapOf<String, ((Result<PCallRequestError?>) -> Unit) -> Unit>()

    fun put(
        callId: String,
        callback: (Result<PCallRequestError?>) -> Unit,
        onTimeout: ((Result<PCallRequestError?>) -> Unit) -> Unit
    ) {
        remove(callId)
        callbacks[callId] = callback
        timeoutHandlers[callId] = onTimeout

        val runnable = Runnable {
            val cb = remove(callId) ?: return@Runnable
            onTimeout(cb)
        }
        timeouts[callId] = runnable

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler.postDelayed(runnable, callId, timeoutMs)
        } else {
            handler.postDelayed(runnable, timeoutMs)
        }
    }

    /**
     * Reschedules the existing timeout for the given callId.
     * Useful when retrying a call to prevent premature timeout firing.
     */
    fun rescheduleTimeout(callId: String) {
        val onTimeout = timeoutHandlers[callId] ?: return
        timeouts.remove(callId)?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            val cb = remove(callId) ?: return@Runnable
            onTimeout(cb)
        }
        timeouts[callId] = runnable

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler.postDelayed(runnable, callId, timeoutMs)
        } else {
            handler.postDelayed(runnable, timeoutMs)
        }
    }

    fun remove(callId: String): ((Result<PCallRequestError?>) -> Unit)? {
        timeouts.remove(callId)?.let { handler.removeCallbacks(it) }
        timeoutHandlers.remove(callId)
        return callbacks.remove(callId)
    }

    fun invokeAndRemove(callId: String, result: Result<PCallRequestError?>) {
        remove(callId)?.invoke(result)
    }

    fun clear() {
        timeouts.values.forEach { handler.removeCallbacks(it) }
        callbacks.clear()
        timeouts.clear()
        timeoutHandlers.clear()
    }

    companion object {
        const val CALLBACK_TIMEOUT_MS = 5000L
    }
}

/** Retry policy: retries only when a SecurityException indicates CALL_PHONE permission issue. */
private object CallPhoneSecurityRetryDecider : RetryDecider {
    override fun shouldRetry(attempt: Int, error: Throwable, maxAttempts: Int): Boolean {
        if (attempt >= maxAttempts) return false
        return error is SecurityException && error.isCallPhoneSecurityException()
    }
}
