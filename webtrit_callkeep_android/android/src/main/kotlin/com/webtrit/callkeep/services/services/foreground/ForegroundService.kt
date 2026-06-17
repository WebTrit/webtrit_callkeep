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
import com.webtrit.callkeep.common.PendingBroadcastQueue
import com.webtrit.callkeep.common.Platform
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.managers.NotificationChannelManager
import com.webtrit.callkeep.models.CallConnectionState
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailedCallInfo
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.InvalidCallMetadataException
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
import com.webtrit.callkeep.services.core.AnswerCallRoute
import com.webtrit.callkeep.services.core.CallkeepCore
import com.webtrit.callkeep.services.core.ConnectionEventListener
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallRelease
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
 * - Registers and unregisters itself via [com.webtrit.callkeep.services.core.CallkeepCore] for connection events.
 */
@Keep
class ForegroundService :
    Service(),
    PHostApi,
    ConnectionEventListener {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val activityWakelockManager = ActivityWakelockManager(ActivityHolder)

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

    // Pigeon callbacks for reportNewIncomingCall() that are waiting for Telecom confirmation.
    // Populated in startIncomingCall.onSuccess instead of resolving immediately, so that
    // Flutter only gets "success" once Telecom has actually accepted the call (IncomingConnectionReported)
    // or gets CALL_REJECTED_BY_SYSTEM when Telecom rejects it (HungUp / onCreateIncomingConnectionFailed).
    private val pendingIncomingCallbacks: ConcurrentHashMap<String, (Result<PIncomingCallError?>) -> Unit> =
        ConcurrentHashMap()

    // Timeout runnables for pending incoming call confirmations, keyed by callId.
    // Allows cancellation when the confirmation arrives before the timeout fires.
    private val pendingIncomingTimeouts: ConcurrentHashMap<String, Runnable> = ConcurrentHashMap()

    /**
     * Resolves a pending [reportNewIncomingCall] Pigeon callback with [result].
     * Cancels the associated safety timeout. Safe to call multiple times — only
     * the first call has any effect (the entry is removed atomically).
     */
    private fun resolvePendingIncomingCallback(
        callId: String,
        result: Result<PIncomingCallError?>,
    ) {
        val cb = pendingIncomingCallbacks.remove(callId) ?: return
        pendingIncomingTimeouts.remove(callId)?.let { mainHandler.removeCallbacks(it) }
        cb(result)
    }

    private var _flutterDelegateApi: PDelegateFlutterApi? = null
    var flutterDelegateApi: PDelegateFlutterApi?
        get() = _flutterDelegateApi
        set(value) {
            _flutterDelegateApi = value
        }

    override fun onConnectionEvent(
        event: ConnectionEvent,
        data: Bundle?,
    ) {
        logger.d("onConnectionEvent: ${event.name}")
        when (event) {
            CallLifecycleEvent.IncomingConnectionReported -> {
                handleCSIncomingConnectionReported(data)
            }

            CallLifecycleEvent.ConnectionStateChanged -> {
                handleCSReportConnectionStateChanged(data)
            }

            CallLifecycleEvent.ReplayIncomingCall -> {
                handleCSReplayIncomingCall(data)
            }

            CallLifecycleEvent.DeclineCall -> {
                handleCSReportDeclineCall(data)
            }

            CallLifecycleEvent.HungUp -> {
                handleCSReportDeclineCall(data)
            }

            CallLifecycleEvent.ConnectionNotFound -> {
                handleCSReportDeclineCall(data)
            }

            CallLifecycleEvent.AnswerCall -> {
                handleCSReportAnswerCall(data)
            }

            CallMediaEvent.AudioDeviceSet -> {
                handleCSReportAudioDeviceSet(data)
            }

            CallMediaEvent.AudioDevicesUpdate -> {
                handleCsReportAudioDevicesUpdate(data)
            }

            CallMediaEvent.AudioMuting -> {
                handleCSReportAudioMuting(data)
            }

            CallMediaEvent.ConnectionHolding -> {
                handleCSReportConnectionHolding(data)
            }

            CallMediaEvent.SentDTMF -> {
                handleCSReportSentDTMF(data)
            }

            else -> { /* per-call events handled by dynamic receivers in startCall() */ }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logger.d("onCreate")
        core.addConnectionEventListener(this)
        isRunning = true
        // Connection-state replay is triggered from onDelegateSet() (the deterministic
        // "delegate ready" signal), not here: at onCreate the Flutter delegate is not yet
        // attached, so a replay fired now would only race the attach.
    }

    override fun setUp(
        options: POptions,
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.i("setUp")
        if (!TelephonyUtils.isTelecomSupported(baseContext)) {
            logger.i("setUp: android.software.telecom not available on this device — skipping phone account registration, using standalone call mode")
            applySetupOptions(options)
            callback(Result.success(Unit))
            return
        }
        registerPhoneAccountWithRetry(options, callback, attempt = 0)
    }

    private fun registerPhoneAccountWithRetry(
        options: POptions,
        callback: (Result<Unit>) -> Unit,
        attempt: Int,
    ) {
        val maxAttempts = 5
        val retryDelayMs = 500L

        try {
            TelephonyUtils(baseContext).registerPhoneAccount()
        } catch (e: Exception) {
            if (attempt < maxAttempts - 1) {
                logger.w("setUp: registerPhoneAccount failed (attempt ${attempt + 1}/$maxAttempts), retrying in ${retryDelayMs}ms: ${e.message}")
                mainHandler.postDelayed({ registerPhoneAccountWithRetry(options, callback, attempt + 1) }, retryDelayMs)
                return
            }
            logger.e("setUp: registerPhoneAccount failed after $maxAttempts attempts", e)
            callback(Result.failure(e))
            return
        }

        logger.i("setUp: registerPhoneAccount succeeded${if (attempt > 0) " on attempt ${attempt + 1}" else ""}")

        applySetupOptions(options)

        callback.invoke(Result.success(Unit))
    }

    private fun applySetupOptions(options: POptions) {
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
            options.android.incomingCallTimeoutMs?.let { StorageDelegate.Timeout.setIncomingCallTimeoutMs(baseContext, it) }
            options.android.outgoingCallTimeoutMs?.let { StorageDelegate.Timeout.setOutgoingCallTimeoutMs(baseContext, it) }
            options.android.logFilePath?.let {
                StorageDelegate.Logging.setLogFilePath(baseContext, it)
                Log.setLogFilePath(it)
                logger.i("applySetupOptions: native logging initialized path=$it")
            }
        }.onFailure { Log.w("CallKeep", "Android options init failed: ${it.message}", it) }
    }

    override fun startCall(
        callId: String,
        handle: PHandle,
        displayNameOrContactIdentifier: String?,
        video: Boolean,
        proximityEnabled: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        val metadata =
            CallMetadata(
                callId = callId,
                handle = handle.toCallHandle(),
                displayName = displayNameOrContactIdentifier,
                hasVideo = video,
                proximityEnabled = proximityEnabled,
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
                    core.unregisterConnectionEvents(baseContext, it)
                } catch (_: IllegalArgumentException) {
                }
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

        val callEventReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
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
                            syncScreenWakelock()
                            val handle = callMetaData.handle
                            if (handle == null) {
                                // Should not happen for a confirmed outgoing call. Fail the request
                                // through the normal channel instead of crashing on handle!! or
                                // leaving a ghost call that Telecom shows but Flutter never learns of.
                                val error =
                                    InvalidCallMetadataException(
                                        "OngoingCall confirmed for ${callMetaData.callId} without a handle",
                                    )
                                logger.e("$logContext: ${error.message}")
                                saveFailedOutgoingCall(metadata, OutgoingFailureSource.CS_CALLBACK, error)
                                finish(Result.failure(error))
                                return
                            }
                            flutterDelegateApi?.performStartCall(
                                callMetaData.callId,
                                handle.toPHandle(),
                                // Pass the resolved label (display name or number), or null when
                                // unknown; the pigeon contract is nullable and the Flutter client
                                // decides how to render an unknown caller.
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
                                metadata,
                                OutgoingFailureSource.CS_CALLBACK,
                                failureMetaData.getThrowable(),
                            )
                            val result =
                                when (failureMetaData.outgoingFailureType) {
                                    OutgoingFailureType.UNENTITLED -> {
                                        Result.failure(failureMetaData.getThrowable())
                                    }

                                    OutgoingFailureType.EMERGENCY_NUMBER -> {
                                        Result.success(PCallRequestError(PCallRequestErrorEnum.EMERGENCY_NUMBER))
                                    }
                                }
                            finish(result)
                        }
                    }
                }
            }
        receiver = callEventReceiver

        core.registerConnectionEvents(
            baseContext,
            listOf(CallLifecycleEvent.OngoingCall, CallLifecycleEvent.OutgoingFailure),
            callEventReceiver,
            exported = false,
        )

        // Add cleanup AFTER receiver is registered to avoid UninitializedPropertyAccessException
        // if onDestroy() fires in the narrow window before receiver assignment.
        pendingCallCleanupsByCallId[callId] = {
            if (resolved.compareAndSet(false, true)) {
                cancelResources()
            }
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
            @SuppressLint("MissingPermission")
            core.startOutgoingCall(metadata)
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
        metadata: CallMetadata,
        source: OutgoingFailureSource,
        error: Throwable?,
    ) = failedCallsStore.add(metadata, source, error?.message)

    override fun reportNewIncomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<PIncomingCallError?>) -> Unit,
    ) {
        logger.i("reportNewIncomingCall: callId=$callId, handle=$handle")

        // Build metadata before the early check so we can promote the call into the core shadow
        // tracker even when the call is already answered (cold-start race: ReplayConnectionStates
        // fires handleCSReportAnswerCall on delegate attach, marking the call answered before
        // reportNewIncomingCall arrives from the signaling layer).
        val ringtonePath = StorageDelegate.Sound.getRingtonePath(baseContext)

        val metadata =
            CallMetadata(
                callId = callId,
                handle = handle.toCallHandle(),
                displayName = displayName,
                hasVideo = hasVideo,
                ringtonePath = ringtonePath,
            )

        // Query tracker state BEFORE addPending, which resets lifecycle flags (answeredCallIds).
        // MainProcessConnectionTracker is the authoritative view of call state in the main process,
        // updated via broadcasts from :callkeep_core. In contrast, checkAndReservePending (inside
        // startIncomingCall) only checks PhoneConnectionService.connectionManager, which is isolated
        // from :callkeep_core and is never updated with answered/terminated transitions.
        //
        // exists() is also checked here to short-circuit duplicate detection without a Telecom
        // round-trip. When IncomingConnectionReported has already been delivered and promoted the call,
        // the second reportNewIncomingCall must return CALL_ID_ALREADY_EXISTS immediately rather
        // than going to Telecom, which would otherwise trigger the CALL_ID_ALREADY_EXISTS adoption
        // path and return null (masking the duplicate from Flutter).
        //
        // isTerminated is intentionally NOT checked here. MainProcessConnectionTracker derives
        // termination from the absence of a callId in all active sets — there is no persistent
        // terminated list. A call that re-arrives with the same ID (e.g. transfer back) must be
        // allowed through regardless of timing.
        val trackerError: PIncomingCallError? = core.checkIncomingDuplicate(callId)
        if (trackerError != null) {
            if (trackerError.value == PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED) {
                // Cold-start race: the call was already answered in Telecom (via the notification
                // button) before reportNewIncomingCall arrived from the signaling layer.
                // Promote the call into the core shadow so endCall() can locate it for the
                // duration of the active call.
                // Fire performAnswerCall directly — the Telecom connection is already ACTIVE,
                // so we bypass the callkeep.answerCall() -> IPC -> AnswerCall broadcast round-trip.
                // __onCallPerformEventAnswered will start WebRTC using the offer from
                // __onCallSignalingEventIncoming, which is emitted to the bloc state just after
                // this callback returns but before _CallPerformEvent.answered is processed.
                core.promote(callId, metadata, PCallkeepConnectionState.STATE_ACTIVE)
                core.markAnswered(callId)
                flutterDelegateApi?.performAnswerCall(callId) {}
                logger.i("reportNewIncomingCall: adopted already-answered call callId=$callId, fired performAnswerCall")
            } else {
                // CALL_ID_ALREADY_EXISTS here is expected in the push+signaling combined flow:
                // the push path registers the call first, and the signaling WebSocket arrives
                // shortly after with the same callId. Logging at INFO avoids spurious
                // Crashlytics exception reports in consuming apps that forward WARN to
                // FirebaseCrashlytics.recordError().
                logger.i("reportNewIncomingCall: rejecting duplicate callId=$callId, tracker state=${trackerError.value}")
            }
            callback(Result.success(trackerError))
            return
        }

        // Pre-register the Pigeon callback and safety timeout BEFORE calling startIncomingCall.
        // IncomingConnectionReported can arrive synchronously — during the addNewIncomingCall Telecom
        // call inside startIncomingCall — before the IPC onSuccess callback returns to this
        // process. Without pre-registration, resolvePendingIncomingCallback finds no entry and
        // the confirmation is lost, causing the 5-second timeout to fire unconditionally.
        //
        // putIfAbsent is used instead of a plain assignment so that concurrent
        // reportNewIncomingCall calls with the same callId (all dispatched on the main thread
        // before any of them completes) cannot overwrite each other's callback. Only the first
        // caller owns the slot (ownsPendingSlot=true) and registers the timeout; duplicates
        // skip both registrations and, in their onError handler, must not touch the maps so
        // the first callback remains in place until IncomingConnectionReported resolves it.
        val ownsPendingSlot = pendingIncomingCallbacks.putIfAbsent(callId, callback) == null
        // Non-owners post no timeout and have nothing to cancel in onError — null makes
        // the ownership contract explicit and avoids allocating a no-op Runnable per call.
        val timeoutRunnable: Runnable? =
            if (ownsPendingSlot) {
                Runnable {
                    logger.w("reportNewIncomingCall: Telecom confirmation timeout for callId=$callId, resolving with CALL_REJECTED_BY_SYSTEM")
                    pendingIncomingTimeouts.remove(callId)
                    // pendingCallIds is owned by InProcessCallkeepCore.startIncomingCall now.
                    // If we got here, neither onSuccess nor onError fired within 5 s — the
                    // internal drain did not run, so we must drain explicitly. removePending
                    // is idempotent.
                    core.removePending(callId)
                    // Mark terminated and endCallDispatched so that a late-arriving HungUp
                    // broadcast (after the timeout) does not cause handleCSReportDeclineCall
                    // to fire performEndCall for a call Flutter already got callRejectedBySystem for.
                    core.clearAndMarkEndCallDispatched(callId)
                    resolvePendingIncomingCallback(
                        callId,
                        Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_REJECTED_BY_SYSTEM)),
                    )
                }.also { r ->
                    pendingIncomingTimeouts[callId] = r
                    mainHandler.postDelayed(r, INCOMING_CALL_CONFIRMATION_TIMEOUT_MS)
                }
            } else {
                null
            }

        // Note: core.startIncomingCall can throw synchronously (e.g. uninitialized
        // ContextHolder). The exception bypasses our onError handler and propagates to
        // Pigeon as channel-error. The 5 s timeoutRunnable above is our safety-net for
        // that case — it fires, drains pending, and resolves pendingIncomingCallbacks
        // with CALL_REJECTED_BY_SYSTEM. (Dart will have already received the original
        // throwable via channel-error by then; the second reply is matched by reply-ID
        // and silently dropped by Flutter.)
        core.startIncomingCall(
            metadata = metadata,
            onSuccess = {
                logger.d("reportNewIncomingCall: startIncomingCall success callId=$callId")
                // pendingIncomingCallbacks and timeout are already registered above.
            },
            onError = { error ->
                // Cancel timeout and clear maps only if this call owns the pending slot.
                // A non-owner (ownsPendingSlot=false) must leave the maps untouched so the
                // first caller's callback stays in place for IncomingConnectionReported to resolve.
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (ownsPendingSlot) {
                    pendingIncomingTimeouts.remove(callId)
                    pendingIncomingCallbacks.remove(callId)
                }

                when (error?.value) {
                    PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS -> {
                        // The callId is still in the main-process ConnectionManager.pendingCallIds
                        // from the original registration by the background isolate, so
                        // checkAndReservePending returns CALL_ID_ALREADY_EXISTS regardless of
                        // whether the call was answered. Use the tracker's last known connection
                        // state — which is NOT reset by core.addPending — to distinguish between
                        // a call that is still ringing and one that was already answered via the
                        // notification Answer button while the main process had no UI running.
                        //
                        // connectionStates[callId] is mirrored to STATE_ACTIVE by the
                        // ConnectionStateChanged event (PhoneConnection.onStateChanged ACTIVE, fired
                        // from :callkeep_core after onAnswer()); updateState writes it unconditionally
                        // so it is preserved across the addPending() call above.
                        val existingState = core.getState(callId)
                        if (existingState == PCallkeepConnectionState.STATE_ACTIVE) {
                            // Call answered before the main app started its UI. Adopt as active
                            // and notify Flutter so it skips the incoming screen entirely.
                            logger.i("reportNewIncomingCall: adopting already-answered call callId=$callId (CALL_ID_ALREADY_EXISTS + STATE_ACTIVE)")
                            core.promote(callId, metadata, PCallkeepConnectionState.STATE_ACTIVE)
                            core.markAnswered(callId)
                            flutterDelegateApi?.performAnswerCall(callId) {}
                            callback(Result.success(null))
                        } else {
                            // Call still ringing in Telecom but not yet promoted in the tracker
                            // (narrow race: Telecom created the PhoneConnection before the
                            // IncomingConnectionReported broadcast was delivered to this process).
                            // Promote into the tracker so answerCall() / endCall() can locate it,
                            // then return CALL_ID_ALREADY_EXISTS so Flutter treats this as a
                            // duplicate rather than a new registration — the call was already
                            // reported to Flutter by the push path's didPushIncomingCall callback.
                            logger.i("reportNewIncomingCall: ringing call already in Telecom callId=$callId, promoting and returning callIdAlreadyExists")
                            core.promote(callId, metadata, PCallkeepConnectionState.STATE_RINGING)
                            callback(
                                Result.success(
                                    PIncomingCallError(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS),
                                ),
                            )
                        }
                    }

                    PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED -> {
                        // The call was already answered (e.g. via the notification Answer button)
                        // while the main process was not running. Adopt it as an active call and
                        // notify Flutter so it can transition its state machine from incoming to
                        // active without waiting for an AnswerCall broadcast that will not arrive.
                        logger.i("reportNewIncomingCall: adopting already-answered call callId=$callId")
                        core.promote(callId, metadata, PCallkeepConnectionState.STATE_ACTIVE)
                        core.markAnswered(callId)
                        flutterDelegateApi?.performAnswerCall(callId) {}
                        callback(Result.success(null))
                    }

                    else -> {
                        logger.e("reportNewIncomingCall: startIncomingCall failed callId=$callId, error=$error")
                        // The pending entry has already been drained by
                        // InProcessCallkeepCore.startIncomingCall before invoking this onError
                        // callback, so no core.removePending(callId) is needed here.
                        callback(Result.success(error))
                    }
                }
            },
        )
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
        // callback guards (directNotified, endCallDispatched).

        // Step 1: Collect active call IDs from the core shadow state (promoted connections).
        val activeCallIds = core.getAll().map { it.callId }

        // Step 1b: Drain any deferred reportNewIncomingCall callbacks that are still waiting
        // for Telecom confirmation. These calls were accepted by startIncomingCall() but
        // IncomingConnectionReported has not yet arrived. Resolve them with CALL_REJECTED_BY_SYSTEM
        // and mark directNotified so that any subsequent HungUp broadcast is suppressed.
        // Must run before drainUnconnectedPendingCallIds() so the callIds are removed from
        // pendingCallIds first, preventing tearDown from also firing performEndCall for them.
        pendingIncomingCallbacks.keys().toList().forEach { callId ->
            logger.w("tearDown: resolving pending incoming callback for callId=$callId with CALL_REJECTED_BY_SYSTEM")
            core.markDirectNotified(callId)
            core.removePending(callId)
            core.clearAndMarkEndCallDispatched(callId)
            resolvePendingIncomingCallback(
                callId,
                Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_REJECTED_BY_SYSTEM)),
            )
        }

        // Step 2: Drain pending calls that were registered with Telecom but whose
        // PhoneConnection was never created (no IncomingConnectionReported received yet).
        val unconnectedPending = core.drainUnconnectedPendingCallIds()

        // Step 3: Notify Flutter for active connections. Mark directNotified
        // BEFORE CallkeepCore/ConnectionService tears down the underlying connections so
        // that any async HungUp broadcast emitted during that teardown phase is suppressed.
        // Also mark terminated + endCallDispatched so that any endCall() arriving during
        // the tearDown window does not re-fire performEndCall or dispatch a duplicate
        // startHungUpCall IPC.
        activeCallIds.forEach { callId ->
            core.markDirectNotified(callId)
            core.clearAndMarkEndCallDispatched(callId)
            flutterDelegateApi?.performEndCall(callId) {}
        }

        // Step 4: Notify Flutter for pending-only calls.
        // Mark in directNotified BEFORE firing performEndCall so that any async HungUp
        // broadcast from connection.hungUp() (Step 5) is suppressed — this happens when
        // the deferred-answer path caused CS to create a PhoneConnection via reserveAnswer
        // even though IncomingConnectionReported had not yet arrived and the callId was still pending.
        // Also mark terminated + endCallDispatched to match the onDestroy path and prevent
        // a duplicate startHungUpCall IPC if endCall() arrives during the tearDown window.
        unconnectedPending.forEach { callId ->
            core.markDirectNotified(callId)
            core.clearAndMarkEndCallDispatched(callId)
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
                core.unregisterConnectionEvents(baseContext, it)
            }
        }

        val tearDownResolved = AtomicBoolean(false)

        fun finishTearDown() {
            if (!tearDownResolved.compareAndSet(false, true)) return
            tearDownTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            tearDownTimeoutRunnable = null
            tearDownAckReceiver?.let {
                runCatching {
                    core.unregisterConnectionEvents(baseContext, it)
                }
            }
            tearDownAckReceiver = null
            // Step 6: Reset tracker state for the next session.
            core.clear()
            syncScreenWakelock()
            // Keep PhoneConnectionService alive for the next session so that its next
            // incoming intents (e.g. AnswerCall) arrive at a live service instance.
            core.tearDownService()
            callback.invoke(Result.success(Unit))
        }

        val ackReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action == CallCommandEvent.TearDownComplete.name) {
                        logger.d("tearDown: received TearDownComplete ack")
                        finishTearDown()
                    }
                }
            }
        tearDownAckReceiver = ackReceiver

        core.registerConnectionEvents(
            baseContext,
            listOf(CallCommandEvent.TearDownComplete),
            ackReceiver,
            exported = false,
        )

        // Safety timeout: if TearDownComplete never arrives (e.g. CS was not running),
        // proceed anyway so tearDown() always resolves.
        val timeoutRunnable =
            Runnable {
                logger.w("tearDown: TearDownComplete ack timed out, proceeding")
                finishTearDown()
            }
        tearDownTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, TEAR_DOWN_ACK_TIMEOUT_MS)

        core.sendTearDownConnections()
    }

    // Only for iOS, not used in Android
    override fun reportConnectingOutgoingCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.i("reportConnectingOutgoingCall: callId=$callId")
        callback.invoke(Result.success(Unit))
    }

    override fun reportConnectedOutgoingCall(
        callId: String,
        callback: (Result<Unit>) -> Unit,
    ) {
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
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.i("reportUpdateCall: callId=$callId")
        val metadata =
            CallMetadata(
                callId = callId,
                handle = handle?.toCallHandle(),
                displayName = displayName,
                hasVideo = hasVideo,
                proximityEnabled = proximityEnabled,
            )
        core.startUpdateCall(metadata)
        syncScreenWakelock()
        callback.invoke(Result.success(Unit))
    }

    override fun reportEndCall(
        callId: String,
        displayName: String,
        reason: PEndCallReason,
        callback: (Result<Unit>) -> Unit,
    ) {
        logger.i("reportEndCall: callId=$callId, reason=$reason")
        val callMetaData = CallMetadata(callId = callId, displayName = displayName)
        // Post a pending release so IncomingCallService.handleLaunch() can detect a stale
        // IC_INITIALIZE that arrives after this call was already terminated. Only posted when
        // IncomingCallService is not yet running — if it is already up, releaseReceiver is
        // registered and will handle IC_RELEASE_WITH_DECLINE directly. Posting when the service
        // is already running creates orphan entries that are never consumed.
        // Must be posted here (main thread) — not in NotificationManager which runs in
        // :callkeep_core and cannot reach this main-process queue.
        if (!IncomingCallService.isRunning) {
            PendingBroadcastQueue.post(PendingBroadcastQueue.incomingReleaseKey(callId))
        }
        core.startDeclineCall(callMetaData)
        callback.invoke(Result.success(Unit))
    }

    override fun answerCall(
        callId: String,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        val metadata = CallMetadata(callId = callId)
        // IncomingConnectionReported is delivered via sendBroadcast() which is async. Between the
        // moment CS creates the PhoneConnection and the moment the broadcast reaches
        // ForegroundService, core.exists() is false even though the call is live on the
        // CS side. Resolution order:
        //   1. AnswerImmediately -> promoted, answer via IPC immediately.
        //   2. DeferAnswer       -> PhoneConnection not yet created, defer via ReserveAnswer.
        //   3. NotFound          -> unknown call, return error.
        when (core.routeAnswerCall(callId)) {
            is AnswerCallRoute.AnswerImmediately -> {
                logger.i("answerCall $callId: connection exists in core shadow, answering immediately.")
                core.startAnswerCall(metadata)
                callback.invoke(Result.success(null))
            }

            is AnswerCallRoute.DeferAnswer -> {
                // Telecom accepted the call but CS has not yet created the PhoneConnection.
                // Reserve the answer in the core shadow and send a ReserveAnswer command to CS so
                // onCreateIncomingConnection can apply it immediately on the :callkeep_core side.
                logger.i("answerCall $callId: pending in core shadow, CS has no connection yet, deferring answer.")
                core.reserveAnswer(callId)
                core.sendReserveAnswer(callId)
                callback.invoke(Result.success(null))
            }

            is AnswerCallRoute.NotFound -> {
                logger.e("answerCall: no connection or pending entry for callId=$callId in core shadow or CS")
                callback.invoke(Result.success(PCallRequestError(PCallRequestErrorEnum.INTERNAL)))
            }
        }
    }

    override fun endCall(
        callId: String,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("endCall $callId.")

        // If there is a deferred reportNewIncomingCall callback waiting for Telecom
        // confirmation, resolve it immediately with null (success). The call was
        // accepted by startIncomingCall() and is now being explicitly ended by the
        // app — the subsequent HungUp broadcast must still fire performEndCall.
        // Without this, handleCSReportDeclineCall would see the pending callback and
        // return CALL_REJECTED_BY_SYSTEM while suppressing performEndCall.
        if (pendingIncomingCallbacks.containsKey(callId)) {
            logger.d("endCall $callId: resolving deferred incoming callback before explicit end")
            resolvePendingIncomingCallback(callId, Result.success(null))
        }

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
                logger.w(
                    "endCall: $callId already terminated and endCall was already dispatched — returning error without re-notifying.",
                )
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
        callId: String,
        key: String,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("sendDTMF: callId=$callId, key=$key")
        val metadata = CallMetadata(callId = callId, dualToneMultiFrequency = key.getOrNull(0))
        core.startSendDtmfCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setMuted(
        callId: String,
        muted: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("setMuted: callId=$callId, muted=$muted")
        val metadata = CallMetadata(callId = callId, hasMute = muted)
        core.startMutingCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setHeld(
        callId: String,
        onHold: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("setHeld: callId=$callId, onHold=$onHold")
        val metadata = CallMetadata(callId = callId, hasHold = onHold)
        core.startHoldingCall(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setSpeaker(
        callId: String,
        enabled: Boolean,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("setSpeaker: callId=$callId, enabled=$enabled")
        val metadata = CallMetadata(callId = callId, hasSpeaker = enabled)
        core.startSpeaker(metadata)
        callback.invoke(Result.success(null))
    }

    override fun setAudioDevice(
        callId: String,
        device: PAudioDevice,
        callback: (Result<PCallRequestError?>) -> Unit,
    ) {
        logger.i("setAudioDevice: callId=$callId, device=$device")
        val metadata =
            CallMetadata(
                callId = callId,
                audioDevice = device.toAudioDevice(),
            )
        core.setAudioDevice(metadata)
        callback.invoke(Result.success(null))
    }

    // --------------------------------
    // Handlers for ConnectionService reports to communicate with the Flutter side
    // --------------------------------
    //

    private fun syncScreenWakelock() {
        val hasVideo = core.getAll().any { it.hasVideo == true }
        if (hasVideo) {
            activityWakelockManager.acquireScreenWakeLock()
        } else {
            activityWakelockManager.releaseScreenWakeLock()
        }
    }

    private fun handleCSIncomingConnectionReported(extras: Bundle?) {
        logger.d("handleCSIncomingConnectionReported")
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            // Register-only: record the connection in the main-process shadow state. The foreground
            // delegate is deliberately NOT notified here. A foreground incoming always reaches the
            // Flutter delegate by another route: its own signaling (__onCallSignalingEventIncoming)
            // for calls that arrive while the app is running, or the ReplayIncomingCall replay on
            // delegate attach for a push->foreground handoff (the connection existed before this
            // process did). Background incoming is shown by IncomingCallService directly. So there is
            // no live push-path delivery to make from this event.
            registerIncomingConnection(metadata)
        }
    }

    /**
     * Register a reported incoming connection in the main-process shadow state: promote it from
     * pending to a fully tracked connection, refresh the screen wakelock, and resolve any deferred
     * reportNewIncomingCall Pigeon callback (the success path: Telecom accepted the call, so Flutter
     * learns it is live via the resolved callback, null = no error).
     */
    private fun registerIncomingConnection(metadata: CallMetadata) {
        core.promote(metadata.callId, metadata, PCallkeepConnectionState.STATE_RINGING)
        syncScreenWakelock()
        resolvePendingIncomingCallback(metadata.callId, Result.success(null))
    }

    /**
     * Notify the Flutter delegate of an incoming call via the public `didPushIncomingCall` callback.
     * The single delivery point to the foreground delegate, used by the connection-state replay
     * ([handleCSReplayIncomingCall]) to seed a freshly attached delegate. Delivery is unconditional:
     * the Dart CallBloc deduplicates by callId, so re-delivering a call the app already knows about
     * (e.g. from signaling) does not create a second ActiveCall.
     */
    private fun deliverIncomingToDelegate(metadata: CallMetadata) {
        val handle = metadata.handle
        if (handle == null) {
            // Cannot build a PHandle without a number; skip rather than crash on a
            // malformed/handle-less broadcast (the call is already promoted in the tracker).
            logger.w("deliverIncomingToDelegate: missing handle for callId=${metadata.callId}; skipping didPushIncomingCall")
            return
        }
        logger.i("deliverIncomingToDelegate: delivering incoming callId=${metadata.callId} to delegate")
        flutterDelegateApi?.didPushIncomingCall(
            handleArg = handle.toPHandle(),
            displayNameArg = metadata.displayName,
            videoArg = metadata.hasVideo ?: false,
            callIdArg = metadata.callId,
            errorArg = null,
        ) {}
    }

    /**
     * Mirror the authoritative connection state carried by ConnectionStateChanged
     * (PhoneConnection.onStateChanged in :callkeep_core, or StandaloneCallService) into the shadow
     * state. Live states only; terminal DISCONNECTED is owned by the cause-carrying HungUp/DeclineCall
     * path (handleCSReportDeclineCall -> markTerminated), so it is ignored here.
     */
    private fun handleCSReportConnectionStateChanged(extras: Bundle?) {
        extras?.let {
            val metadata = CallMetadata.fromBundle(it)
            val state = metadata.connectionState ?: return@let
            if (state == CallConnectionState.DISCONNECTED) return@let
            logger.d("handleCSReportConnectionStateChanged: callId=${metadata.callId} state=$state")
            core.updateState(metadata.callId, state)
        }
    }

    /**
     * Deliver a still-ringing incoming call to the (freshly attached) Flutter delegate. This is the
     * sole foreground delivery of an incoming call: triggered by
     * [com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent.ReplayIncomingCall] from
     * [com.webtrit.callkeep.services.services.connection.PhoneConnectionService.handleReplayConnectionStates]
     * on delegate attach (e.g. a push->foreground isolate handoff or hot restart). The delegate that
     * received the original report is gone, so the new one must be seeded. A duplicate is harmless --
     * the Flutter CallBloc deduplicates by callId and enriches the existing entry with the signaling
     * offer when it arrives.
     */
    private fun handleCSReplayIncomingCall(extras: Bundle?) {
        logger.d("handleCSReplayIncomingCall")
        extras?.let { deliverIncomingToDelegate(CallMetadata.fromBundle(it)) }
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
            if (core.consumeDirectNotified(callId)) {
                logger.d(
                    "handleCSReportDeclineCall: suppressing stale broadcast for callId=$callId (already notified directly)",
                )
                return@let
            }

            // If there is a pending reportNewIncomingCall callback for this callId, Telecom
            // rejected the call before Flutter was ever notified of it. Resolve the Pigeon
            // callback with CALL_REJECTED_BY_SYSTEM and return early — do NOT fire
            // performEndCall since Flutter never received a successful registration.
            if (pendingIncomingCallbacks.containsKey(callId)) {
                logger.w(
                    "handleCSReportDeclineCall: Telecom rejected callId=$callId before Flutter confirmation — resolving with CALL_REJECTED_BY_SYSTEM",
                )
                core.removePending(callId)
                // Mark terminated and endCallDispatched so that a subsequent endCall()
                // by Flutter (after receiving callRejectedBySystem) does not re-fire
                // performEndCall — performEndCall must never fire for a call that was
                // never confirmed to Flutter.
                core.clearAndMarkEndCallDispatched(callId)
                resolvePendingIncomingCallback(
                    callId,
                    Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_REJECTED_BY_SYSTEM)),
                )
                return@let
            }

            // Mark terminated and record that performEndCall is being dispatched now,
            // so that a subsequent endCall() call with isTerminated=true does NOT
            // re-fire performEndCall. Without this, if onCreateIncomingConnectionFailed
            // fires a HungUp broadcast while the call is still in the pending window
            // (before Dart calls endCall), the broadcast fires performEndCall once here
            // AND the endCall re-fire path fires it a second time — producing a duplicate
            // delegate callback.
            core.clearAndMarkEndCallDispatched(callId)
            syncScreenWakelock()

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
            flutterDelegateApi?.didActivateAudioSession {}

            if (IncomingCallService.isRunning) {
                // Push-notification path: tell the background isolate to release its
                // signaling WebSocket. ActivityHolder.start() already fired from
                // PhoneConnection.onAnswer() in :callkeep_core.
                IncomingCallService.release(baseContext, IncomingCallRelease.IC_RELEASE_WITH_ANSWER)
            }
            flutterDelegateApi?.performAnswerCall(callMetaData.callId) {}
        }
    }

    private fun handleCSReportAudioDeviceSet(extras: Bundle?) {
        logger.d("handleCSReportAudioDeviceSet")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            val audioDevice = callMetaData.audioDevice ?: run {
                logger.w("handleCSReportAudioDeviceSet: audioDevice not set for callId=${callMetaData.callId}")
                return
            }
            flutterDelegateApi?.performAudioDeviceSet(
                callMetaData.callId,
                audioDevice.toPAudioDevice(),
            ) {}
        }
    }

    private fun handleCsReportAudioDevicesUpdate(extras: Bundle?) {
        logger.d("handleCsReportAudioDevicesUpdate")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performAudioDevicesUpdate(
                callMetaData.callId,
                callMetaData.audioDevices.map { audioDevice -> audioDevice.toPAudioDevice() },
            ) {}
        }
    }

    private fun handleCSReportAudioMuting(extras: Bundle?) {
        logger.d("handleCSReportAudioMuting")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSetMuted(
                callMetaData.callId,
                callMetaData.hasMute ?: false,
            ) {}
        }
    }

    private fun handleCSReportConnectionHolding(extras: Bundle?) {
        logger.d("handleCSReportConnectionHolding")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            val onHold = callMetaData.hasHold ?: false
            // The HOLDING / ACTIVE shadow state is mirrored from the real connection via
            // ConnectionStateChanged (onStateChanged for Telecom; explicit emit from StandaloneCallService),
            // so this handler only relays the hold action to Flutter.
            flutterDelegateApi?.performSetHeld(callMetaData.callId, onHold) {}
        }
    }

    private fun handleCSReportSentDTMF(extras: Bundle?) {
        logger.d("handleCSReportSentDTMF")
        extras?.let {
            val callMetaData = CallMetadata.fromBundle(it)
            flutterDelegateApi?.performSendDTMF(
                callMetaData.callId,
                callMetaData.dualToneMultiFrequency.toString(),
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
        logger.d("onDelegateSet: Flutter delegate attached. Replaying connection state...")

        // Replay the current connection lifecycle now that the delegate is attached and GUARANTEED
        // to receive the re-fired events. This is the single replay trigger: it fires only once the
        // Flutter delegate is ready (so re-delivery is not raced/dropped) and on every attach,
        // including a warm engine re-attach. It is deliberately NOT gated on the local tracker
        // below: in a push->foreground handoff the main process has no record of the call yet --
        // the replay from :callkeep_core is exactly what surfaces it to the freshly attached engine.
        core.replayConnectionStates()

        // Ask :callkeep_core to re-emit audio state (device + mute) for any active connections.
        // PhoneConnection.forceUpdateAudioState() runs in the :callkeep_core process and sends
        // CallMediaEvent broadcasts back to the main process, which updates the Flutter UI.
        // Not gated on the main-process tracker: handleReplayAudioState() iterates the live
        // :callkeep_core connections, so it is already a no-op when there are none -- and the
        // local tracker is transiently empty right after the replay above (and during a
        // push->foreground handoff), which would otherwise skip the re-sync exactly when needed.
        core.replayAudioState()
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
        core.removeConnectionEventListener(this)

        pendingCallCleanupsByCallId.values.toList().forEach { it() }
        pendingCallCleanupsByCallId.clear()

        // Resolve any deferred reportNewIncomingCall callbacks that are still pending.
        // The service is being destroyed so Telecom confirmation will never arrive.
        // Mirror the tearDown path: mark directNotified and remove from pending so
        // that stale HungUp broadcasts from the dying CS process are suppressed and
        // pendingCallIds do not leak into the next session's core state.
        pendingIncomingCallbacks.keys().toList().forEach { callId ->
            logger.w("onDestroy: resolving pending incoming callback for callId=$callId with CALL_REJECTED_BY_SYSTEM")
            core.markDirectNotified(callId)
            core.removePending(callId)
            core.clearAndMarkEndCallDispatched(callId)
            resolvePendingIncomingCallback(
                callId,
                Result.success(PIncomingCallError(PIncomingCallErrorEnum.CALL_REJECTED_BY_SYSTEM)),
            )
        }

        // Cancel any in-progress tearDown so the receiver and timeout do not outlive the service.
        tearDownTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        tearDownTimeoutRunnable = null
        tearDownAckReceiver?.let {
            runCatching {
                core.unregisterConnectionEvents(baseContext, it)
            }
        }
        tearDownAckReceiver = null

        activityWakelockManager.dispose()

        // Phone account registration is tied to the user session, not the service lifecycle.
        // Unregistration happens only in finishTearDown() (explicit logout/tearDown call).
        // Removing it here ensures cold-start push calls work even if the service was killed.

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

        // Maximum time to wait for Telecom to confirm an incoming call via IncomingConnectionReported.
        // If this elapses without confirmation or rejection, resolve the Pigeon callback with
        // CALL_REJECTED_BY_SYSTEM to avoid leaking the deferred callback.
        private const val INCOMING_CALL_CONFIRMATION_TIMEOUT_MS = 5_000L

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

    fun add(
        metadata: CallMetadata,
        source: OutgoingFailureSource,
        reason: String?,
    ) {
        logger.w("add: callId=${metadata.callId}, source=$source, reason=$reason")
        val info =
            FailedCallInfo(
                callId = metadata.callId,
                metadata = metadata,
                source = source,
                reason = reason,
            )
        store[metadata.callId] = info
    }

    fun getAll(): List<FailedCallInfo> = store.values.toList().sortedByDescending { it.timestamp }
}
