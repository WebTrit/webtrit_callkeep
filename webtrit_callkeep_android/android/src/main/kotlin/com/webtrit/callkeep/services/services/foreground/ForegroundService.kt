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
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.PEndCallReason
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHostApi
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
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
import com.webtrit.callkeep.services.broadcaster.CallCommandEvent
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.CallMediaEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.core.CallkeepCore

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

    // Stored as fields so onDestroy() can cancel the timeout and unregister the receiver
    // if the service is destroyed before the TearDownComplete ack arrives.
    private var tearDownAckReceiver: BroadcastReceiver? = null
    private var tearDownTimeoutRunnable: Runnable? = null

    private val binder = LocalBinder()

    // Per-call cleanup lambdas keyed by callId. Each entry cancels the per-call timeout
    // and unregisters the per-call receiver without invoking the Pigeon callback (the
    // service is being destroyed, so the channel is already gone). Populated in startCall()
    // after the receiver is registered, and removed in finish() when the call resolves
    // normally. Using a map instead of a set allows cancelling a previous pending call
    // when startCall() is invoked again with the same callId.
    private val pendingCallCleanupsByCallId: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap()

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
                CallLifecycleEvent.ConnectionNotFound.name -> handleCSReportDeclineCall(intent.extras)
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
        // IncomingFailure is not handled here and is excluded to avoid noise.
        val globalEvents: List<ConnectionEvent> = listOf(
            CallLifecycleEvent.DidPushIncomingCall,
            CallLifecycleEvent.DeclineCall,
            CallLifecycleEvent.HungUp,
            CallLifecycleEvent.ConnectionNotFound,
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
            // Only persist a field when the caller explicitly provides a value.
            // A null option means "unspecified / leave as-is"; it must not overwrite a
            // previously persisted value on each setUp() call.
            options.android.ringtoneSound?.let { StorageDelegate.Sound.initRingtonePath(baseContext, it) }
            options.android.ringbackSound?.let { StorageDelegate.Sound.initRingbackPath(baseContext, it) }
            options.android.incomingCallFullScreen?.let { StorageDelegate.IncomingCall.setFullScreen(baseContext, it) }
        }.onFailure { Log.w("CallKeep", "Android options init failed: ${it.message}", it) }

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

        // Register as pending so answerCall/endCall can locate this call via the core shadow
        // before the outgoing connection is confirmed by ConnectionService.
        core.addPending(callId)

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
            // Remove from pending regardless of outcome. On the success path (OngoingCall)
            // promote() has already removed the callId from pendingCallIds, so this is a
            // no-op. On failure/timeout paths the pending entry would otherwise linger until
            // tearDown(), causing drainUnconnectedPendingCallIds() to fire a spurious
            // performEndCall and routing answerCall() into the deferred-answer path.
            core.removePending(callId)
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
                        // Outgoing call is now active in Telecom — promote from pending.
                        core.promote(callMetaData.callId, callMetaData, PCallkeepConnectionState.STATE_DIALING)
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
            @SuppressLint("MissingPermission") core.startOutgoingCall(metadata)
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

        // Query tracker state BEFORE addPending, which resets lifecycle flags (answeredCallIds,
        // terminatedCallIds). MainProcessConnectionTracker is the authoritative view of call state
        // in the main process, updated via broadcasts from :callkeep_core. In contrast,
        // checkAndReservePending (inside startIncomingCall) only checks
        // PhoneConnectionService.connectionManager, which is isolated from :callkeep_core and
        // is never updated with answered/terminated transitions — so it cannot detect these states.
        val trackerError: PIncomingCallError? = when {
            core.isTerminated(callId) ->
                PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED)
            core.isAnswered(callId) ->
                PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED)
            else -> null
        }
        if (trackerError != null) {
            logger.w("reportNewIncomingCall: rejecting callId=$callId, tracker state=${trackerError.value}")
            callback(Result.success(trackerError))
            return
        }

        val ringtonePath = StorageDelegate.Sound.getRingtonePath(baseContext)

        val metadata = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            ringtonePath = ringtonePath
        )

        // Register as pending before sending to Telecom so that answerCall() / endCall()
        // issued before DidPushIncomingCall fires can locate the call via core.isPending().
        // addPending returns true only if this invocation actually inserted the entry, so the
        // rollback in onError does not remove a concurrent genuine pending registration for the
        // same callId (e.g. a second reportNewIncomingCall racing against the first).
        val addedPending = core.addPending(callId)

        core.startIncomingCall(
            metadata = metadata,
            onSuccess = {
                logger.d("reportNewIncomingCall: startIncomingCall success callId=$callId")
                // Mark this callId as signaling-registered so that the DidPushIncomingCall
                // broadcast (which arrives later via the :callkeep_core IPC round-trip) does
                // not fire an additional didPushIncomingCall Pigeon call. Flutter already
                // learns about this call from __onCallSignalingEventIncoming.
                core.markSignalingRegistered(callId)
                callback(Result.success(null))
            },
            onError = { error ->
                logger.e("reportNewIncomingCall: startIncomingCall failed callId=$callId, error=$error")
                // Roll back the pending entry only if this invocation added it. This avoids
                // removing a genuine pending entry registered by a concurrent first invocation
                // when a duplicate call's error arrives.
                if (addedPending) core.removePending(callId)
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
        // core.clear() at the end of tearDown handles all per-session state including
        // callback guards (directNotified, endCallDispatched, signalingRegistered).

        // Step 1: Collect active call IDs from the core shadow state (promoted connections).
        val activeCallIds = core.getAll().map { it.callId }

        // Step 2: Drain pending calls that were registered with Telecom but whose
        // PhoneConnection was never created (no DidPushIncomingCall received yet).
        val unconnectedPending = core.drainUnconnectedPendingCallIds()

        // Step 3: Notify Flutter for active connections. Mark in directNotified
        // BEFORE calling connection.hungUp() so the async HungUp broadcast is suppressed.
        activeCallIds.forEach { callId ->
            core.markDirectNotified(callId)
            flutterDelegateApi?.performEndCall(callId) {}
        }

        // Step 4: Notify Flutter for pending-only calls.
        // Mark in directNotified BEFORE firing performEndCall so that any async HungUp
        // broadcast from connection.hungUp() (Step 5) is suppressed — this happens when
        // the deferred-answer path caused CS to create a PhoneConnection via reserveAnswer
        // even though DidPushIncomingCall had not yet arrived and the callId was still pending.
        unconnectedPending.forEach { callId ->
            core.markDirectNotified(callId)
            flutterDelegateApi?.performEndCall(callId) {}
        }

        if (activeCallIds.isNotEmpty() || unconnectedPending.isNotEmpty()) {
            flutterDelegateApi?.didDeactivateAudioSession {}
        }

        // Step 5: Send TearDownConnections command to :callkeep_core via startService.
        // PhoneConnectionService will call hungUp() on all its PhoneConnections,
        // cleanConnections(), and reply with TearDownComplete.
        // We wait for the ack (or a short timeout) before resetting tracker state
        // so that the next session is not started with stale connection objects.

        // Cancel any in-progress tearDown from a previous invocation before setting up a new one.
        tearDownTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        tearDownAckReceiver?.let {
            runCatching {
                ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, it)
            }
        }

        val tearDownResolved = AtomicBoolean(false)

        fun finishTearDown() {
            if (!tearDownResolved.compareAndSet(false, true)) return
            tearDownTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            tearDownTimeoutRunnable = null
            tearDownAckReceiver?.let {
                runCatching {
                    ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, it)
                }
            }
            tearDownAckReceiver = null
            // Step 6: Reset tracker state for the next session.
            core.clear()
            // Keep PhoneConnectionService alive for the next session so that its next
            // incoming intents (e.g. AnswerCall) arrive at a live service instance.
            core.tearDownService()
            callback.invoke(Result.success(Unit))
        }

        tearDownAckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == CallCommandEvent.TearDownComplete.name) {
                    logger.d("tearDown: received TearDownComplete ack")
                    finishTearDown()
                }
            }
        }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallCommandEvent.TearDownComplete),
            baseContext,
            tearDownAckReceiver!!,
            exported = false,
        )

        // Safety timeout: if TearDownComplete never arrives (e.g. CS was not running),
        // proceed anyway so tearDown() always resolves.
        tearDownTimeoutRunnable = Runnable {
            logger.w("tearDown: TearDownComplete ack timed out, proceeding")
            finishTearDown()
        }
        mainHandler.postDelayed(tearDownTimeoutRunnable!!, TEAR_DOWN_ACK_TIMEOUT_MS)

        core.sendTearDownConnections()
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
        core.startEstablishCall(metadata)
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
        core.startUpdateCall(metadata)
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
        core.startDeclineCall(callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        val metadata = CallMetadata(callId = callId)
        // DidPushIncomingCall is delivered via sendBroadcast() which is async. Between the
        // moment CS creates the PhoneConnection and the moment the broadcast reaches
        // ForegroundService, core.exists() is false even though the call is live on the
        // CS side. Resolution order:
        //   1. core.exists()     -> promoted, answer immediately.
        //   2. core.isPending()  -> PhoneConnection not yet created, defer via ReserveAnswer.
        //   3. none of the above -> unknown call, return error.
        when {
            core.exists(callId) -> {
                logger.i("answerCall $callId: connection exists in core shadow, answering immediately.")
                core.startAnswerCall(metadata)
                callback.invoke(Result.success(null))
            }
            core.isPending(callId) -> {
                // Telecom accepted the call but CS has not yet created the PhoneConnection.
                // Reserve the answer in the core shadow and send a ReserveAnswer command to CS so
                // onCreateIncomingConnection can apply it immediately on the :callkeep_core side.
                logger.i("answerCall $callId: pending in core shadow, CS has no connection yet, deferring answer.")
                core.reserveAnswer(callId)
                core.sendReserveAnswer(callId)
                callback.invoke(Result.success(null))
            }
            else -> {
                logger.e("answerCall: no connection or pending entry for callId=$callId in core shadow or CS")
                callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
            }
        }
    }

    override fun endCall(callId: String, callback: (Result<PCallRequestError?>) -> Unit) {
        logger.i("endCall $callId.")
        if (core.isTerminated(callId)) {
            // Re-fire performEndCall only on the first endCall for a Telecom-terminated call
            // (e.g. onCreateIncomingConnectionFailed fired before the Dart callback was registered).
            // markEndCallDispatched guards against a second explicit endCall() re-firing the event
            // and inflating the delegate's endCallIds count.
            val isFirstEndCall = core.markEndCallDispatched(callId)
            if (isFirstEndCall) {
                logger.w("endCall: $callId terminated by Telecom before endCall was dispatched — re-notifying Flutter.")
                flutterDelegateApi?.performEndCall(callId) {}
            } else {
                logger.w("endCall: $callId already terminated and endCall was already dispatched — returning error without re-notifying.")
            }
            callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.UNKNOWN_CALL_UUID)))
            return
        }
        core.markEndCallDispatched(callId)
        val metadata = CallMetadata(callId = callId)
        core.startHungUpCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun sendDTMF(
        callId: String, key: String, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("sendDTMF: callId=$callId, key=$key")
        val metadata = CallMetadata(callId = callId, dualToneMultiFrequency = key.getOrNull(0))
        core.startSendDtmfCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(
        callId: String, muted: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setMuted: callId=$callId, muted=$muted")
        val metadata = CallMetadata(callId = callId, hasMute = muted)
        core.startMutingCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(
        callId: String, onHold: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setHeld: callId=$callId, onHold=$onHold")
        val metadata = CallMetadata(callId = callId, hasHold = onHold)
        core.startHoldingCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        callId: String, enabled: Boolean, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setSpeaker: callId=$callId, enabled=$enabled")
        val metadata = CallMetadata(callId = callId, hasSpeaker = enabled)
        core.startSpeaker(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setAudioDevice(
        callId: String, device: PAudioDevice, callback: (Result<PCallRequestError?>) -> Unit
    ) {
        logger.i("setAudioDevice: callId=$callId, device=$device")
        val metadata = CallMetadata(
            callId = callId, audioDevice = device.toAudioDevice()
        )
        core.setAudioDevice(metadata)
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
            // Promote from pending to fully registered incoming connection.
            core.promote(metadata.callId, metadata, PCallkeepConnectionState.STATE_RINGING)

            // If this call was registered via reportNewIncomingCall (the foreground signaling
            // path), Flutter already knows about it through __onCallSignalingEventIncoming.
            // Suppress didPushIncomingCall to prevent a duplicate push-path entry (line -1)
            // from being added to state.activeCalls alongside the existing signaling entry
            // (line 0). In the :callkeep_core separate-process architecture, this broadcast
            // arrives AFTER the reportNewIncomingCall Pigeon response (IPC round-trip latency),
            // so without this guard the push-path handler always runs after the signaling
            // handler and creates a second ActiveCall for the same callId.
            if (core.consumeSignalingRegistered(metadata.callId)) {
                logger.d("handleCSReportDidPushIncomingCall: suppressing didPushIncomingCall for signaling-registered call ${metadata.callId}")
                return@let
            }

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

            // consumeSignalingRegistered cleans up any pending signaling guard for this
            // callId (edge case: call terminates before DidPushIncomingCall arrives).
            core.consumeSignalingRegistered(callId)

            // Suppress stale async HungUp/Decline broadcasts for calls that were already
            // directly notified via performEndCall in tearDown(). Without this guard, the
            // broadcast from the previous session's connection.hungUp() arrives after the
            // new session's delegate is set and fires performEndCall for the wrong callId.
            if (core.consumeDirectNotified(callId)) {
                logger.d("handleCSReportDeclineCall: suppressing stale broadcast for callId=$callId (already notified directly)")
                return@let
            }

            // Update tracker: call has ended.
            core.markTerminated(callId)

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
            // Consume any deferred answer reservation (set by the answerCall deferred path).
            // This mirrors ConnectionManager.consumeAnswer so pendingAnswers does not leak.
            core.consumeAnswer(callMetaData.callId)
            // Update tracker: call has been answered.
            core.markAnswered(callMetaData.callId)
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
            val onHold = callMetaData.hasHold ?: false
            // Keep tracker state in sync so getConnections() reflects HOLDING / ACTIVE correctly.
            core.markHeld(callMetaData.callId, onHold)
            flutterDelegateApi?.performSetHeld(callMetaData.callId, onHold) {}
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
        val connections = core.getAll()

        if (connections.isEmpty()) {
            Log.d(TAG, "onDelegateSet: No active connections found.")
            return
        }

        // Ask :callkeep_core to re-emit audio state (device + mute) for all active connections.
        // PhoneConnection.forceUpdateAudioState() runs in the :callkeep_core process and sends
        // CallMediaEvent broadcasts back to the main process, which updates the Flutter UI.
        core.sendSyncAudioState()
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

        // Cancel any in-progress tearDown so the receiver and timeout do not outlive the service.
        tearDownTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        tearDownTimeoutRunnable = null
        tearDownAckReceiver?.let {
            runCatching {
                ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, it)
            }
        }
        tearDownAckReceiver = null

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
        private const val TEAR_DOWN_ACK_TIMEOUT_MS = 3_000L

        /**
         * Process-wide facade for all interactions with the `:callkeep_core` process.
         *
         * Provides a single access point for both reading shadow connection state and
         * sending commands to [PhoneConnectionService]. After the process split, swap
         * [CallkeepCore.instance] to change the IPC strategy without touching call sites.
         */
        val core: CallkeepCore get() = CallkeepCore.instance
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
