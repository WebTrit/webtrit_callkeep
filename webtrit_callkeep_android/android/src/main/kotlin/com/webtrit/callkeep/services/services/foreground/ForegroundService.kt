package com.webtrit.callkeep.services.services.foreground

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailedCallInfo
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureSource
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.models.toAudioDevice
import com.webtrit.callkeep.models.toCallHandle
import com.webtrit.callkeep.models.toPAudioDevice
import com.webtrit.callkeep.models.toPHandle
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
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

    private val binder = LocalBinder()

    // Per-call cleanup lambdas keyed by callId. Each entry cancels the per-call timeout
    // and unregisters the per-call receiver without invoking the Pigeon callback (the
    // service is being destroyed, so the channel is already gone). Populated in startCall()
    // after the receiver is registered, and removed in finish() when the call resolves
    // normally. Using a map instead of a set allows cancelling a previous pending call
    // when startCall() is invoked again with the same callId.
    private val pendingCallCleanupsByCallId: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap()

    // Call IDs for which performEndCall was fired directly in tearDown().
    // Used to suppress the subsequent stale async HungUp broadcast that arrives
    // via connectionServicePerformReceiver after the new session has already started.
    private val directNotifiedCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
        }

    private val connectionServicePerformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            logger.d("connectionServicePerformReceiver onReceive: $action")
            when (action) {
                CallLifecycleEvent.DidPushIncomingCall.name -> handleCSReportDidPushIncomingCall(
                    intent.extras
                )

                CallLifecycleEvent.DeclineCall.name -> handleCSReportDeclineCall(intent.extras)
                CallLifecycleEvent.HungUp.name -> handleCSReportDeclineCall(intent.extras)
                CallLifecycleEvent.AnswerCall.name -> handleCSReportAnswerCall(intent.extras)
                CallMediaEvent.AudioDeviceSet.name -> handleCSReportAudioDeviceSet(intent.extras)
                CallMediaEvent.AudioDevicesUpdate.name -> handleCsReportAudioDevicesUpdate(intent.extras)
                CallMediaEvent.AudioMuting.name -> handleCSReportAudioMuting(intent.extras)
                CallMediaEvent.ConnectionHolding.name -> handleCSReportConnectionHolding(intent.extras)
                CallMediaEvent.SentDTMF.name -> handleCSReportSentDTMF(intent.extras)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logger.d("onCreate")
        // Register only the events that connectionServicePerformReceiver actually handles.
        // OngoingCall/OutgoingFailure go to per-call receivers in startCall().
        // IncomingFailure/ConnectionNotFound are not handled here and are excluded to avoid noise.
        val globalEvents: List<ConnectionEvent> = listOf(
            CallLifecycleEvent.DidPushIncomingCall,
            CallLifecycleEvent.DeclineCall,
            CallLifecycleEvent.HungUp,
            CallLifecycleEvent.AnswerCall,
            CallMediaEvent.AudioDeviceSet,
            CallMediaEvent.AudioDevicesUpdate,
            CallMediaEvent.AudioMuting,
            CallMediaEvent.ConnectionHolding,
            CallMediaEvent.SentDTMF,
        )
        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            globalEvents, baseContext, connectionServicePerformReceiver
        )
        isRunning = true
    }

    override fun setUp(options: POptions, callback: (Result<Unit>) -> Unit) {
        logger.i("setUp")

        try {
            TelephonyUtils(baseContext).registerPhoneAccount()
        } catch (e: Exception) {
            logger.e("setUp: registerPhoneAccount failed", e)
            callback(Result.failure(e))
            return
        }

        runCatching {
            // Registers all necessary notification channels for the application.
            // This includes channels for active calls, incoming calls, missed calls, and foreground calls.
            NotificationChannelManager.registerNotificationChannels(baseContext)
        }.onFailure { Log.w("CallKeep", "Channel registration failed: ${it.message}", it) }

        runCatching {
            // Only update the stored paths when the caller explicitly provides a value.
            // A null option means "unspecified / leave as-is"; it must not erase a
            // previously persisted custom sound on each setUp() call.
            options.android.ringtoneSound?.let { StorageDelegate.Sound.initRingtonePath(baseContext, it) }
            options.android.ringbackSound?.let { StorageDelegate.Sound.initRingbackPath(baseContext, it) }
            options.android.incomingCallFullScreen?.let { StorageDelegate.IncomingCall.setFullScreen(baseContext, it) }
        }.onFailure { Log.w("CallKeep", "Sound init failed: ${it.message}", it) }

        callback.invoke(Result.success(Unit))
    }

    override fun startCall(
        callId: String,
        handle: PHandle,
        displayNameOrContactIdentifier: String?,
        video: Boolean,
        proximityEnabled: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit
    ) {
        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayNameOrContactIdentifier,
            hasVideo = video,
            proximityEnabled = proximityEnabled
        )

        val logContext = "startCall($callId|$handle)"
        logger.i("$logContext: trying to start call")

        // Cancel any previous pending call for this callId before creating a new one.
        // This prevents duplicate receivers/timeouts if startCall() is invoked again with the same callId.
        pendingCallCleanupsByCallId.remove(callId)?.invoke()

        // Each outgoing call owns its own receiver + AtomicBoolean so that the callback
        // and performStartCall are invoked exactly once, regardless of whether the
        // ConnectionService responds before or after the timeout fires.
        val handler = Handler(Looper.getMainLooper())
        val resolved = AtomicBoolean(false)
        var receiver: BroadcastReceiver? = null

        fun cancelResources() {
            handler.removeCallbacksAndMessages(null)
            // Guard against the window where the cleanup is invoked before receiver
            // is registered (i.e., between pendingCallCleanupsByCallId.put and registerConnectionPerformReceiver).
            receiver?.let {
                try {
                    ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, it)
                } catch (_: IllegalArgumentException) {}
            }
        }

        fun finish(result: Result<PCallRequestError?>) {
            if (!resolved.compareAndSet(false, true)) return
            pendingCallCleanupsByCallId.remove(callId)
            cancelResources()
            callback(result)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Acquire resolution before any side effects so that a stale broadcast
                // arriving after a timeout cannot trigger performStartCall or saveFailedOutgoingCall.
                if (resolved.get()) return
                when (intent?.action) {
                    CallLifecycleEvent.OngoingCall.name -> {
                        val callMetaData = CallMetadata.fromBundle(intent.extras ?: return)
                        if (callMetaData.callId != callId) return
                        logger.i("$logContext: ongoing call confirmed by CS")
                        flutterDelegateApi?.performStartCall(
                            callMetaData.callId,
                            callMetaData.handle!!.toPHandle(),
                            callMetaData.name,
                            callMetaData.hasVideo ?: false,
                        ) {}
                        finish(Result.success(null))
                    }
                    CallLifecycleEvent.OutgoingFailure.name -> {
                        val failureMetaData = FailureMetadata.fromBundle(intent.extras ?: return)
                        if (failureMetaData.callMetadata?.callId != callId) return
                        logger.e("$logContext: CS reported failure: ${failureMetaData.outgoingFailureType}")
                        saveFailedOutgoingCall(
                            metadata, OutgoingFailureSource.CS_CALLBACK, failureMetaData.getThrowable()
                        )
                        val result = when (failureMetaData.outgoingFailureType) {
                            OutgoingFailureType.UNENTITLED ->
                                Result.failure(failureMetaData.getThrowable())
                            OutgoingFailureType.EMERGENCY_NUMBER ->
                                Result.success(PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER))
                        }
                        finish(result)
                    }
                }
            }
        }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.OngoingCall, CallLifecycleEvent.OutgoingFailure),
            baseContext,
            receiver!!,
            exported = false,
        )

        // Add cleanup AFTER receiver is registered to avoid UninitializedPropertyAccessException
        // if onDestroy() fires in the narrow window before receiver assignment.
        pendingCallCleanupsByCallId[callId] = {
            if (resolved.compareAndSet(false, true)) { cancelResources() }
        }

        handler.postDelayed({
            val exception = Exception("Overall timeout reached")
            logger.w("$logContext: timeout", exception)
            saveFailedOutgoingCall(metadata, OutgoingFailureSource.TIMEOUT, exception)
            finish(Result.success(PCallRequestError(PCallRequestErrorEnum.TIMEOUT)))
        }, OUTGOING_CALL_TIMEOUT_MS)

        try {
            // Suppress MissingPermission lint: self-managed PhoneAccount does not require
            // CALL_PHONE permission — the Telecom framework handles the call directly.
            @SuppressLint("MissingPermission") PhoneConnectionService.startOutgoingCall(
                baseContext, metadata
            )
            logger.i("$logContext: startOutgoingCall dispatched")
        } catch (e: EmergencyNumberException) {
            logger.e("$logContext failed: emergency number", e)
            saveFailedOutgoingCall(metadata, OutgoingFailureSource.DISPATCH_ERROR, e)
            finish(Result.success(PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER)))
        } catch (e: Exception) {
            logger.e("$logContext failed: ${e.javaClass.simpleName}: ${e.message}", e)
            saveFailedOutgoingCall(metadata, OutgoingFailureSource.DISPATCH_ERROR, e)
            finish(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
        }
    }

    /**
     * Saves information about a failed outgoing call to an in-memory store for diagnostics.
     *
     * @param metadata The [CallMetadata] associated with the failed call attempt.
     * @param source The [OutgoingFailureSource] indicating where the failure was detected
     * (e.g., timeout, ConnectionService callback).
     * @param error The [Throwable] that caused the failure, if available. Its message is extracted for logging.
     */
    private fun saveFailedOutgoingCall(
        metadata: CallMetadata, source: OutgoingFailureSource, error: Throwable?
    ) = failedCallsStore.add(metadata, source, error?.message)

    // TODO: Move logic to the PhoneConnectionService
    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit
    ) {
        logger.i("reportNewIncomingCall: callId=$callId, handle=$handle")

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
            onSuccess = {
                logger.d("reportNewIncomingCall: startIncomingCall success callId=$callId")
                callback(Result.success(null))
            },
            onError = { error ->
                logger.e("reportNewIncomingCall: startIncomingCall failed callId=$callId, error=$error")
                callback(Result.success(error))
            })
    }

    override fun isSetUp(): Boolean = true

    override fun tearDown(callback: (Result<Unit>) -> Unit) {
        logger.i("tearDown")

        // Synchronously notify Flutter and clean up connections before returning.
        //
        // Why not rely on async HungUp broadcasts:
        //   connection.hungUp() sends an async HungUp broadcast that can arrive AFTER the
        //   next session's delegate is registered, causing stale performEndCall for the wrong callId.
        //
        // Why not send PhoneConnectionService.tearDown(baseContext):
        //   That enqueues a TearDown intent processed asynchronously. Its handleTearDown()
        //   calls cleanConnections() which can clear the next session's pendingCallIds,
        //   causing a concurrent reportNewIncomingCall to slip through a second time.
        //
        // Solution: fire performEndCall directly here, register each callId in
        // directNotifiedCallIds so the stale async HungUp broadcast is suppressed
        // in handleCSReportDeclineCall.
        // Clear stale entries from previous sessions. If a broadcast for a prior session's
        // callId never arrived, those entries would linger indefinitely and could suppress
        // legitimate broadcasts if callIds are ever reused.
        directNotifiedCallIds.clear()

        val connections = PhoneConnectionService.connectionManager.getConnections()
        connections.forEach { connection ->
            val callId = connection.callId
            // Register BEFORE hungUp() so the async HungUp broadcast is already suppressed
            // by the time it arrives in handleCSReportDeclineCall.
            directNotifiedCallIds.add(callId)
            // Notify Flutter synchronously while this session's delegate is still set.
            flutterDelegateApi?.performEndCall(callId) {}
            // Trigger native cleanup (cancel notifications, ringtone, etc.).
            connection.hungUp()
        }

        // Drain pending calls that never got a PhoneConnection object.
        val unconnected = PhoneConnectionService.connectionManager.drainUnconnectedPendingCallIds()
        unconnected.forEach { callId ->
            flutterDelegateApi?.performEndCall(callId) {}
        }

        if (connections.isNotEmpty() || unconnected.isNotEmpty()) {
            flutterDelegateApi?.didDeactivateAudioSession {}
        }

        PhoneConnectionService.connectionManager.cleanConnections()

        // Send TearDown intent to keep PhoneConnectionService alive for the next session.
        // Telecom unbinds ConnectionService when all connections are destroyed, and without
        // a startService call to keep it warm, the next session's intents (e.g. AnswerCall)
        // arrive at a dead/restarted service with empty connection state.
        //
        // handleTearDown() is safe because it no longer calls cleanConnections() or
        // drainUnconnectedPendingCallIds() — those are already done synchronously above.
        PhoneConnectionService.tearDown(baseContext)
        callback.invoke(Result.success(Unit))
    }

    // Only for iOS, not used in Android
    override fun reportConnectingOutgoingCall(
        callId: String, callback: (Result<Unit>) -> Unit
    ) {
        logger.i("reportConnectingOutgoingCall: callId=$callId")
        callback.invoke(Result.success(Unit))
    }

    override fun reportConnectedOutgoingCall(callId: String, callback: (Result<Unit>) -> Unit) {
        logger.i("reportConnectedOutgoingCall: callId=$callId")
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
        logger.i("reportUpdateCall: callId=$callId")
        val metadata = CallMetadata(
            callId = callId,
            handle = handle?.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            proximityEnabled = proximityEnabled,
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
        logger.i("reportEndCall: callId=$callId, reason=$reason")
        val callMetaData = CallMetadata(callId = callId, displayName = displayName)
        PhoneConnectionService.startDeclineCall(baseContext, callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId)
        when {
            PhoneConnectionService.connectionManager.isConnectionAlreadyExists(callId) -> {
                logger.i("answerCall $callId: connection exists, answering immediately.")
                PhoneConnectionService.startAnswerCall(baseContext, metadata)
                callback.invoke(Result.success(null))
            }
            PhoneConnectionService.connectionManager.isPending(callId) -> {
                // onCreateIncomingConnection has not fired yet. Reserve the answer so that
                // PhoneConnectionService applies it as soon as the connection is created.
                logger.i("answerCall $callId: connection pending, deferring answer.")
                PhoneConnectionService.connectionManager.reserveAnswer(callId)
                callback.invoke(Result.success(null))
            }
            else -> {
                logger.e("answerCall: no connection or pending entry for callId=$callId")
                callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
            }
        }
    }

    override fun endCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        logger.i("endCall $callId.")
        if (PhoneConnectionService.connectionManager.isConnectionDisconnected(callId)) {
            logger.w("endCall: connection $callId is already terminated.")
            callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.UNKNOWN_CALL_UUID)))
            return
        }
        val metadata = CallMetadata(callId = callId)
        PhoneConnectionService.startHungUpCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("sendDTMF: callId=$callId, key=$key")
        val metadata = CallMetadata(callId = callId, dualToneMultiFrequency = key.getOrNull(0))
        PhoneConnectionService.startSendDtmfCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(
        callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setMuted: callId=$callId, muted=$muted")
        val metadata = CallMetadata(callId = callId, hasMute = muted)
        PhoneConnectionService.startMutingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(
        callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setHeld: callId=$callId, onHold=$onHold")
        val metadata = CallMetadata(callId = callId, hasHold = onHold)
        PhoneConnectionService.startHoldingCall(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setSpeaker: callId=$callId, enabled=$enabled")
        val metadata = CallMetadata(callId = callId, hasSpeaker = enabled)
        PhoneConnectionService.startSpeaker(baseContext, metadata)
        callback.invoke(Result.success(null))
    }

    override fun setAudioDevice(
        callId: String, device: PAudioDevice, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setAudioDevice: callId=$callId, device=$device")
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
        logger.d("handleCSReportDidPushIncomingCall")
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            flutterDelegateApi?.didPushIncomingCall(
                handleArg = metadata.handle!!.toPHandle(),
                displayNameArg = metadata.displayName,
                videoArg = metadata.hasVideo ?: false,
                callIdArg = metadata.callId,
                errorArg = null
            ) {}
        }
    }

    private fun handleCSReportDeclineCall(extras: Bundle?) {
        logger.d("handleCSReportDeclineCall")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            val callId = callMetaData.callId

            // Suppress stale async HungUp/Decline broadcasts for calls that were already
            // directly notified via performEndCall in tearDown(). Without this guard, the
            // broadcast from the previous session's connection.hungUp() arrives after the
            // new session's delegate is set and fires performEndCall for the wrong callId.
            if (directNotifiedCallIds.remove(callId)) {
                logger.d("handleCSReportDeclineCall: suppressing stale broadcast for callId=$callId (already notified directly)")
                return@let
            }

            flutterDelegateApi?.performEndCall(callId) {}
            flutterDelegateApi?.didDeactivateAudioSession {}

            if (Platform.isLockScreen(baseContext)) {
                ActivityHolder.finish()
            }
        }
    }

    private fun handleCSReportAnswerCall(extras: Bundle?) {
        logger.d("handleCSReportAnswerCall")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAnswerCall(callMetaData.callId) {}
            flutterDelegateApi?.didActivateAudioSession {}
        }
    }

    private fun handleCSReportAudioDeviceSet(extras: Bundle?) {
        logger.d("handleCSReportAudioDeviceSet")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAudioDeviceSet(
                callMetaData.callId, callMetaData.audioDevice!!.toPAudioDevice()
            ) {}
        }
    }

    private fun handleCsReportAudioDevicesUpdate(extras: Bundle?) {
        logger.d("handleCsReportAudioDevicesUpdate")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAudioDevicesUpdate(
                callMetaData.callId,
                callMetaData.audioDevices.map { audioDevice -> audioDevice.toPAudioDevice() }) {}
        }
    }

    private fun handleCSReportAudioMuting(extras: Bundle?) {
        logger.d("handleCSReportAudioMuting")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetMuted(
                callMetaData.callId, callMetaData.hasMute ?: false
            ) {}
        }
    }

    private fun handleCSReportConnectionHolding(extras: Bundle?) {
        logger.d("handleCSReportConnectionHolding")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetHeld(
                callMetaData.callId, callMetaData.hasHold ?: false
            ) {}
        }
    }

    private fun handleCSReportSentDTMF(extras: Bundle?) {
        logger.d("handleCSReportSentDTMF")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSendDTMF(
                callMetaData.callId, callMetaData.dualToneMultiFrequency.toString()
            ) {}
        }
    }

    /**
     * Callback triggered from the Flutter side when the delegate is set.
     *
     * This method is invoked when the Flutter application is ready to receive events from the native side.
     * It checks for any existing active connections (calls) and restores their state on the Flutter side.
     * This is crucial for re-synchronizing the UI after a hot restart or when the app comes to the
     * foreground and re-establishes its communication channel with this service.
     */
    override fun onDelegateSet() {
        logger.d("onDelegateSet: Flutter delegate attached. Checking for active connections to restore...")
        val connections = PhoneConnectionService.connectionManager.getConnections()

        if (connections.isEmpty()) {
            Log.d(TAG, "onDelegateSet: No active connections found.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            connections.forEach { connection ->
                val handle = connection.handle

                if (handle == null) {
                    Log.w(TAG, "onDelegateSet: Skipping connection with null handle")
                    return@forEach
                }

                connection.forceUpdateAudioState()
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
        logger.d("onDestroy")
        // Unregister the service from receiving connection service perform events
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(
            baseContext, connectionServicePerformReceiver
        )

        pendingCallCleanupsByCallId.values.toList().forEach { it() }
        pendingCallCleanupsByCallId.clear()

        isRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val TAG = "ForegroundService"

        private val logger = Log(TAG)

        val failedCallsStore = FailedCallsStore()

        var isRunning = false

        private const val OUTGOING_CALL_TIMEOUT_MS = 5_000L
    }
}

/**
 * A thread-safe in-memory store for failed outgoing calls.
 *
 * This class provides a simple, volatile storage mechanism to log details about outgoing call
 * attempts that did not succeed. Since it uses a [ConcurrentHashMap], it is safe for use
 * across multiple threads.
 *
 * The store is in-memory only, meaning its contents are lost when the application process
 * is terminated. It is intended for short-term diagnostics and debugging rather than
 * persistent call logging.
 *
 */
class FailedCallsStore {
    private val logger = Log("FailedCallsStore")
    private val store = ConcurrentHashMap<String, FailedCallInfo>()

    fun add(metadata: CallMetadata, source: OutgoingFailureSource, reason: String?) {
        logger.w("add: callId=${metadata.callId}, source=$source, reason=$reason")
        val info = FailedCallInfo(
            callId = metadata.callId, metadata = metadata, source = source, reason = reason
        )
        store[metadata.callId] = info
    }

    fun getAll(): List<FailedCallInfo> = store.values.toList().sortedByDescending { it.timestamp }
}
