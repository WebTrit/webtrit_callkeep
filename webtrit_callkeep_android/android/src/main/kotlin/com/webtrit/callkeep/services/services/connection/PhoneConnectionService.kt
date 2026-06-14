package com.webtrit.callkeep.services.services.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.InvalidCallMetadataException
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.services.broadcaster.CallCommandEvent
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import com.webtrit.callkeep.services.services.connection.dispatchers.ConnectionLifecycleAction
import com.webtrit.callkeep.services.services.connection.dispatchers.PhoneConnectionServiceDispatcher
import com.webtrit.callkeep.services.services.foreground.ForegroundService

/**
 * `PhoneConnectionService` is a service class responsible for managing phone call connections
 * in the Webtrit CallKeep Android library. It handles incoming and outgoing calls,
 * call actions (answer, decline, mute, hold, etc.), and provides methods for interacting with
 * phone call connections.
 *
 * @constructor Creates a new instance of `PhoneConnectionService`.
 */
class PhoneConnectionService : ConnectionService() {
    private lateinit var phoneConnectionServiceDispatcher: PhoneConnectionServiceDispatcher

    private val dispatcher: ConnectionServicePerformBroadcaster.DispatchHandle =
        ConnectionServicePerformBroadcaster.handle

    override fun onCreate() {
        super.onCreate()
        // Initialize ContextHolder for the :callkeep_core process. Each OS process has its own
        // JVM, so ContextHolder.init() called in the main process has no effect here.
        ContextHolder.init(applicationContext)
        Log.initFromContext(applicationContext)
        // Initialize AssetCacheManager for the :callkeep_core process so that
        // PhoneConnection.onShowIncomingCallUi() can resolve the custom ringtone asset
        // path via AssetCacheManager.getAsset(). Without this, AssetCacheManager.getAsset()
        // may throw IllegalStateException, which is caught inside getRingtone() and causes
        // a fallback to the system default ringtone.
        AssetCacheManager.init(applicationContext)
        // Set the service state to true when the system starts the service.
        isRunning = true

        val proximitySensorManager =
            ProximitySensorManager(applicationContext, PhoneConnectionConsts())

        phoneConnectionServiceDispatcher =
            PhoneConnectionServiceDispatcher(
                connectionManager,
                ::performEventHandle,
                proximitySensorManager,
            )
    }

    /**
     * Handles an event related to a call connection and dispatches it to the appropriate components.
     *
     * This method should be used to report events back to subscribers. If the connection reference
     * still exists, use it directly to handle the event. However, in cases where the connection
     * was destroyed due to concurrency (e.g., another component removed the connection before this
     * component tried to access it), this method serves as a proxy to forward the event via the
     * [PhoneConnectionServiceDispatcher].
     *
     * Using this proxy avoids potential freezes caused by unhandled async/await logic on the Flutter side.
     *
     * @param event The connection-related event to be handled.
     * @param data Optional call metadata associated with the event.
     */
    fun performEventHandle(
        event: ConnectionEvent,
        data: CallMetadata? = null,
    ) {
        Log.i(TAG, "performEventHandle: $event")
        dispatcher.dispatch(baseContext, event, data?.toBundle())
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Parse the intent into a typed command once, BEFORE the try, but without touching the
        // call metadata for commands that do not need it. This avoids the crash where
        // CallMetadata.fromBundle was eagerly invoked on empty Binder-delivered extras for the
        // no-extras lifecycle commands and threw an uncaught IllegalArgumentException.
        val command =
            intent?.let { PhoneServiceCommand.from(it) } ?: run {
                Log.w(TAG, "onStartCommand: unknown, missing, or incomplete action '${intent?.action}', ignoring")
                return START_NOT_STICKY
            }

        try {
            when (command) {
                // IPC commands from the main process — handled directly, not routed through the
                // call-connection dispatcher. Using startService (instead of broadcasts) guarantees
                // delivery even if the service is starting up: the intent is queued and processed
                // after onCreate() completes, so these handlers are always reachable.
                is PhoneServiceCommand.TearDown -> {
                    handleTearDownConnections()
                }

                is PhoneServiceCommand.Reserve -> {
                    handleReserveAnswer(command.callId)
                }

                is PhoneServiceCommand.Pending -> {
                    handleNotifyPending(command.callId)
                }

                // startService() on a ConnectionService from the same app (same UID) is valid:
                // android:permission="BIND_TELECOM_CONNECTION_SERVICE" restricts cross-UID
                // bindService() callers only. Same-UID processes bypass the permission check
                // unconditionally in ActiveServices.startServiceLocked() -> checkComponentPermission().
                // AOSP: frameworks/base/services/core/java/com/android/server/am/ActiveServices.java
                //
                // The official telecom lifecycle only describes the framework-initiated bind path:
                // "Telecom will bind to a ConnectionService implementation when it wants that
                //  ConnectionService to place a call." (developer.android.com/develop/connectivity/telecom/dialer-app)
                // startService() -> onStartCommand() is an orthogonal IPC channel used throughout
                // this codebase (NotifyPending, TearDownConnections, ReserveAnswer, etc.) and is
                // not prohibited by the framework.
                is PhoneServiceCommand.AddIncoming -> {
                    handleAddNewIncomingCall(command.metadata)
                }

                is PhoneServiceCommand.Clean -> {
                    handleCleanConnections()
                }

                is PhoneServiceCommand.SyncAudio -> {
                    handleSyncAudioState()
                }

                is PhoneServiceCommand.SyncConnection -> {
                    handleSyncConnectionState()
                }

                is PhoneServiceCommand.CallOp -> {
                    phoneConnectionServiceDispatcher.dispatch(command.action, command.metadata)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception $e with service action: ${intent.action},")
        }

        return START_NOT_STICKY
    }

    /**
     * Creates an outgoing phone connection for a call and updates the call state based on the provided metadata.
     *
     * @param connectionManagerPhoneAccount The phone account handle for the connection manager.
     * @param request The connection request containing call information.
     * @return The created PhoneConnection object for the outgoing call.
     */
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest,
    ): Connection {
        // request.extras originates from our own placeOutgoingCall(metadata.toBundle()), so a
        // missing callId here is a "should never happen" invariant violation (e.g. Binder
        // truncation / framework edge case). Fail this one connection gracefully instead of
        // letting CallMetadata.fromBundle throw an uncaught IllegalArgumentException that would
        // crash the whole :callkeep_core process.
        val metadata =
            CallMetadata.fromBundleOrNull(request.extras) ?: run {
                Log.e(TAG, "onCreateOutgoingConnection: missing callId in request extras, rejecting")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
            }

        // Check if a connection with the same call ID already exists.
        // If so, reject the new connection request to prevent conflicts.
        if (connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            // Return a failed connection indicating the line is busy.
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        val connection =
            PhoneConnection.createOutgoingPhoneConnection(
                applicationContext,
                ::performEventHandle,
                metadata,
                ::disconnectConnection,
            )
        connectionManager.addConnection(metadata.callId, connection)
        phoneConnectionServiceDispatcher.dispatchLifecycle(
            ConnectionLifecycleAction.ConnectionCreated,
            metadata,
        )

        return connection
    }

    /**
     * Called when the creation of an outgoing connection fails. This method handles the failure by
     * notifying the TelephonyForegroundCallkeepApi about the failure and then calls the superclass
     * implementation.
     *
     * @param connectionManagerPhoneAccount The phone account handle for the connection manager.
     * @param request The connection request that failed.
     */
    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        val callMetadata = CallMetadata.fromBundleOrNull(request?.extras ?: Bundle.EMPTY)

        val failureContext = "onCreateOutgoingConnectionFailed"
        val failureMessage = "$failureContext: $connectionManagerPhoneAccount $request"
        val failureMetadata = FailureMetadata(callMetadata, failureMessage).toBundle()

        Log.e(TAG, failureMessage)

        dispatcher.dispatch(baseContext, CallLifecycleEvent.OutgoingFailure, failureMetadata)

        phoneConnectionServiceDispatcher.dispatchLifecycle(ConnectionLifecycleAction.ConnectionChanged)

        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    /**
     * Create an incoming connection and handle its initialization.
     *
     * @param connectionManagerPhoneAccount The phone account handle for the connection manager.
     * @param request The connection request containing extras.
     * @return The created Connection instance.
     */
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest,
    ): Connection {
        // request.extras originates from our own addNewIncomingCall(metadata.toBundle()), so a
        // missing callId here is a "should never happen" invariant violation. Reject this one
        // connection instead of letting CallMetadata.fromBundle throw an uncaught
        // IllegalArgumentException that would crash the whole :callkeep_core process.
        val metadata =
            CallMetadata.fromBundleOrNull(request.extras) ?: run {
                Log.e(TAG, "onCreateIncomingConnection: missing callId in request extras, rejecting")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
            }
        Log.i(TAG, "onCreateIncomingConnection: entry callId=${metadata.callId} account=$connectionManagerPhoneAccount")

        // Guard against stale Telecom callbacks that arrive after a tearDown.
        //
        // handleAddNewIncomingCall calls addPendingForIncomingCall before addNewIncomingCall,
        // both in the same onStartCommand invocation on the main thread. onCreateIncomingConnection
        // is also dispatched on the main thread, so isPending is true in the normal path.
        //
        // Strategy:
        //   - isPending == true  : normal path (common case).
        //   - isPending == false AND isForcedTerminated : stale post-tearDown callback — reject.
        //   - isPending == false AND NOT isForcedTerminated : defense-in-depth fallback (should
        //     not happen with AddNewIncomingCall IPC, but kept for safety).
        if (!connectionManager.isPending(metadata.callId)) {
            if (connectionManager.isForcedTerminated(metadata.callId)) {
                Log.w(TAG, "onCreateIncomingConnection: callId=${metadata.callId} force-terminated by tearDown, rejecting stale callback")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.LOCAL))
            }
            Log.d(TAG, "onCreateIncomingConnection: callId=${metadata.callId} not pending yet, registering as defense-in-depth fallback")
            connectionManager.addPendingForIncomingCall(metadata.callId)
        }

        // Check if a connection with the same ID already exists.
        // This can occur if receivers from both the activity and the service
        // trigger the incoming call flow simultaneously.
        if (connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            // Clean up pending state to avoid leaks — returning a failed Connection
            // does NOT trigger onCreateIncomingConnectionFailed.
            Log.w(TAG, "onCreateIncomingConnection: callId=${metadata.callId} — connection already exists, returning ERROR")
            connectionManager.removePending(metadata.callId)
            connectionManager.consumeAnswer(metadata.callId)
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }

        // Check if there is already an existing incoming connection.
        // If so, decline the new incoming connection to prevent conflicts in initializing the incoming call flow.
        if (connectionManager.isExistsIncomingConnection()) {
            // Clean up pending state to avoid leaks — returning a failed Connection
            // does NOT trigger onCreateIncomingConnectionFailed.
            Log.w(TAG, "onCreateIncomingConnection: callId=${metadata.callId} — another incoming connection already exists, returning BUSY")
            connectionManager.removePending(metadata.callId)
            connectionManager.consumeAnswer(metadata.callId)
            // Notify the main process that this call was rejected so it can clean
            // up its pending state. Without this, MainProcessConnectionTracker retains
            // the callId in pendingCallIds and a subsequent answerCall() sends a
            // ReserveAnswer that is never consumed (no onCreateIncomingConnection fires again).
            // This mirrors the HungUp path in onCreateIncomingConnectionFailed.
            dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, metadata.toBundle())
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        val connection =
            PhoneConnection.createIncomingPhoneConnection(
                applicationContext,
                ::performEventHandle,
                metadata,
                ::disconnectConnection,
            )

        // Remove from pendingCallIds first (independent of the answer-reservation check).
        // The call is no longer "pending" — it now has a live PhoneConnection object.
        // Removing it from pendingCallIds ensures that a subsequent reportNewIncomingCall
        // for the same callId (e.g. main process CallBloc arriving ~6 s after the push
        // isolate already answered the call) skips the pendingCallIds branch in
        // checkAndReservePending and correctly reaches the hasAnswered check, returning
        // CALL_ID_ALREADY_EXISTS_AND_ANSWERED instead of CALL_ID_ALREADY_EXISTS.
        connectionManager.removePending(metadata.callId)

        // Atomically register the connection and consume any deferred answer reserved by
        // handleReserveAnswer. Using a single lock operation prevents the race where
        // handleReserveAnswer checks getConnection (null) then onCreateIncomingConnection
        // adds the connection + consumeAnswer (false) then handleReserveAnswer reserves —
        // leaving the answer permanently stuck in pendingAnswers with no consumer.
        if (connectionManager.addConnectionAndConsumeAnswer(metadata.callId, connection)) {
            // Schedule onAnswer() for the next main-thread loop iteration, AFTER
            // onCreateIncomingConnection returns to Telecom. Calling setActive() inside
            // onCreateIncomingConnection races with Telecom's own handleCreateConnectionComplete:
            // Telecom resets the call to RINGING after the callback returns, so our setActive()
            // (sent before the return) is overwritten. By posting to the handler we guarantee
            // Telecom has finished its setup before we send setActive(), so a subsequent
            // addNewIncomingCall for a second call sees the first call as ACTIVE (not RINGING)
            // and does not cancel it with DISCONNECTED/CANCELED.
            Log.i(TAG, "onCreateIncomingConnection: scheduling deferred answer after Telecom setup for callId=${metadata.callId}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Log.i(TAG, "onCreateIncomingConnection: applying deferred answer for callId=${metadata.callId}")
                connection.onAnswer()
            }
        }

        phoneConnectionServiceDispatcher.dispatchLifecycle(
            ConnectionLifecycleAction.ConnectionCreated,
            metadata,
        )

        return connection
    }

    /**
     * Called when the creation of an incoming connection fails. This method handles the failure by
     * notifying the TelephonyForegroundCallkeepApi about the failure and then calls the superclass
     * implementation.
     *
     * @param connectionManagerPhoneAccount The phone account handle for the connection manager.
     * @param request The connection request that failed.
     */
    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        val callMetadata = CallMetadata.fromBundleOrNull(request?.extras ?: Bundle.EMPTY)
        val callId = callMetadata?.callId

        // Check before removing: if this callId was pending, the failure was for a real call
        // that should be reported to Flutter as ended (e.g., rejected with BUSY because another
        // incoming call was already ringing). If it was not pending, this is a stale Telecom
        // callback from a previous session (guarded by isPending in onCreateIncomingConnection)
        // and should be silently dropped.
        // pendingCallIds is populated in handleAddNewIncomingCall before addNewIncomingCall, so
        // this check correctly distinguishes legitimate failures from stale callbacks.
        val wasPending = callId != null && connectionManager.isPending(callId)
        callId?.let { connectionManager.removePending(it) }

        val failureContext = "onCreateIncomingConnectionFailed"
        val failureMessage = "$failureContext: callId=$callId wasPending=$wasPending account=$connectionManagerPhoneAccount"

        Log.e(TAG, "$failureMessage — Telecom rejected the incoming call registration")

        if (wasPending) {
            // wasPending = callId != null && isPending(callId), so both are non-null here.
            // Notify Flutter that this call ended so it can clean up its call state.
            Log.i(TAG, "onCreateIncomingConnectionFailed: firing HungUp for pending callId=$callId")
            dispatchHungUpAndRemovePending(callId!!, callMetadata!!, removePending = false)
        } else {
            val failureMetadata = FailureMetadata(callMetadata, failureMessage).toBundle()
            dispatcher.dispatch(baseContext, CallLifecycleEvent.IncomingFailure, failureMetadata)
        }

        phoneConnectionServiceDispatcher.dispatchLifecycle(ConnectionLifecycleAction.ConnectionChanged)

        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    private fun disconnectConnection(connection: PhoneConnection) {
        Log.i(TAG, "disconnectConnection:: $connection")

        phoneConnectionServiceDispatcher.dispatchLifecycle(ConnectionLifecycleAction.ConnectionChanged)
    }

    private fun handleTearDownConnections() {
        Log.i(TAG, "handleTearDownConnections: hanging up all connections and cleaning up")
        val connections = connectionManager.getConnections()
        connections.forEach { connection ->
            runCatching { connection.hungUp() }
                .onFailure { e -> Log.e(TAG, "handleTearDownConnections: hungUp failed for ${connection.callId}", e) }
        }
        connectionManager.cleanConnections()
        dispatcher.dispatch(baseContext, CallCommandEvent.TearDownComplete)
    }

    private fun handleReserveAnswer(callId: String) {
        Log.i(TAG, "handleReserveAnswer: callId=$callId")
        // reserveOrGetConnectionToAnswer is atomic: it either returns the existing connection
        // for immediate answering, or reserves the deferred answer in pendingAnswers under
        // the same lock that addConnectionAndConsumeAnswer uses. This eliminates the race
        // where getConnection returns null, onCreateIncomingConnection adds the connection
        // and consumeAnswer returns false, then reserveAnswer adds to pendingAnswers
        // permanently (no consumer will ever drain it).
        val connection = connectionManager.reserveOrGetConnectionToAnswer(callId)
        if (connection != null) {
            Log.i(TAG, "handleReserveAnswer: connection exists, answering immediately for callId=$callId")
            connection.onAnswer()
        } else {
            Log.d(TAG, "handleReserveAnswer: no connection yet, deferred answer reserved for callId=$callId")
        }
    }

    /**
     * Registers [callId] as pending in :callkeep_core's [ConnectionManager].
     *
     * Called via a [ServiceAction.NotifyPending] startService intent sent by the main process
     * just before [TelephonyUtils.addNewIncomingCall] is called. This ensures that
     * [onCreateIncomingConnection]'s isPending gate accepts the incoming connection even though
     * [ConnectionManager.checkAndReservePending] ran in the main-process JVM (a separate
     * [ConnectionManager] instance).
     */
    private fun handleNotifyPending(callId: String) {
        Log.i(TAG, "handleNotifyPending: callId=$callId")
        connectionManager.addPendingForIncomingCall(callId)
    }

    /**
     * Registers [metadata.callId] as pending and calls [TelephonyUtils.addNewIncomingCall] from
     * within :callkeep_core, invoked via a [ServiceAction.AddNewIncomingCall] startService intent.
     *
     * Running both operations inside this process means they share the same Telecom binder
     * connection. Because Telecom processes binder calls from the same connection in FIFO order,
     * any preceding [android.telecom.Connection.destroy] call (also from :callkeep_core) is
     * guaranteed to be processed before [TelephonyUtils.addNewIncomingCall]. This prevents
     * [onCreateIncomingConnectionFailed] from firing on callId reuse after a declined call.
     *
     * On [TelephonyUtils.addNewIncomingCall] failure, [CallLifecycleEvent.HungUp] is dispatched
     * so the main process resolves the pending Pigeon callback.
     */
    private fun handleAddNewIncomingCall(metadata: CallMetadata) {
        val callId = metadata.callId
        Log.i(TAG, "handleAddNewIncomingCall: callId=$callId")
        // addPendingForIncomingCall returns false if cleanConnections() already ran and placed
        // this callId into forcedTerminatedCallIds. That means the TearDownConnections intent
        // was processed before this AddNewIncomingCall intent -- a stale in-flight IPC from
        // a session that has already been torn down. In that case we must not call
        // addNewIncomingCall: doing so would create a PhoneConnection for a closed session
        // whose HungUp/DidPushIncomingCall broadcasts would arrive in an already-cleared
        // tracker in the main process.
        if (!connectionManager.addPendingForIncomingCall(callId)) {
            Log.w(TAG, "handleAddNewIncomingCall: callId=$callId rejected (post-tearDown stale intent), dispatching HungUp")
            dispatchHungUpAndRemovePending(callId, metadata, removePending = false)
            return
        }
        try {
            TelephonyUtils(baseContext).addNewIncomingCall(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "handleAddNewIncomingCall: addNewIncomingCall failed for callId=$callId, dispatching HungUp", e)
            dispatchHungUpAndRemovePending(callId, metadata)
        }
    }

    /**
     * Removes [callId] from [connectionManager]'s pending set (when [removePending] is true)
     * and dispatches [CallLifecycleEvent.HungUp] so the main process resolves the pending
     * Pigeon callback for this call.
     *
     * Centralises the removePending + HungUp dispatch pattern that appears in multiple
     * failure paths ([handleAddNewIncomingCall], [onCreateIncomingConnectionFailed], etc.)
     * so each site cannot accidentally omit one of the two steps.
     *
     * [metadata] is used as the bundle payload when available, giving receivers full call
     * context (handle, displayName, etc.). Pass [removePending] = false when the pending
     * slot was never added (e.g. [addPendingForIncomingCall] returned false).
     */
    private fun dispatchHungUpAndRemovePending(
        callId: String,
        metadata: CallMetadata,
        removePending: Boolean = true,
    ) {
        if (removePending) connectionManager.removePending(callId)
        dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, metadata.toBundle())
    }

    private fun handleCleanConnections() {
        Log.i(TAG, "handleCleanConnections: clearing all connections")
        connectionManager.cleanConnections()
    }

    private fun handleSyncAudioState() {
        Log.i(TAG, "handleSyncAudioState: re-emitting audio state for all active connections")
        connectionManager.getConnections().forEach { it.forceUpdateAudioState() }
    }

    private fun handleSyncConnectionState() {
        Log.i(TAG, "handleSyncConnectionState: re-emitting lifecycle state for answered connections")
        connectionManager.getConnections().forEach { connection ->
            if (connection.hasAnswered) {
                performEventHandle(CallLifecycleEvent.AnswerCall, CallMetadata(callId = connection.callId))
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        cleanupResources()
        super.onDestroy()
    }

    /**
     * Called when the user removes the application from the recent tasks list (swipes away the app).
     * This ensures that active connections are forcefully disconnected to remove the system call UI
     * and the service is stopped to prevent it from running as a zombie process.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved: App swiped away. Force cleaning connections.")

        cleanupResources()
        stopSelf()

        super.onTaskRemoved(rootIntent)
    }

    /**
     * Initiates cleanup of resources and active connections by updating service state
     * and dispatching a ServiceDestroyed lifecycle event to the dispatcher.
     *
     * This unifies the cleanup flow for both onDestroy and onTaskRemoved. Actual resource
     * release and disconnection of active calls are performed by the
     * phoneConnectionServiceDispatcher in response to the ServiceDestroyed event.
     */
    private fun cleanupResources() {
        // Update service state
        isRunning = false
        phoneConnectionServiceDispatcher.dispatchLifecycle(ConnectionLifecycleAction.ServiceDestroyed)
    }

    companion object {
        private const val TAG = "PhoneConnectionService"

        // The service state is used to determine if the service is running. This is useful to avoid invoking onStartCommand when the service is down.
        private var _isRunning = false

        var isRunning: Boolean
            get() = _isRunning
            private set(value) {
                _isRunning = value
            }

        var connectionManager: ConnectionManager = ConnectionManager()

        fun startAnswerCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.AnswerCall, metadata)
        }

        fun startEstablishCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.EstablishCall, metadata)
        }

        fun startUpdateCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.UpdateCall, metadata)
        }

        fun startDeclineCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.DeclineCall, metadata)
        }

        fun startHungUpCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.HungUpCall, metadata)
        }

        fun startSendDtmfCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.SendDTMF, metadata)
        }

        fun startMutingCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.Muting, metadata)
        }

        fun startHoldingCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.Holding, metadata)
        }

        fun startSpeaker(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.Speaker, metadata)
        }

        fun setAudioDevice(
            context: Context,
            metadata: CallMetadata,
        ) {
            communicate(context, ServiceAction.AudioDeviceSet, metadata)
        }

        fun tearDown(context: Context) {
            communicate(context, ServiceAction.TearDown, null)
        }

        /**
         * Sends a [ServiceAction.TearDownConnections] command to this service via [startService].
         *
         * Using an explicit [startService] intent (instead of a broadcast) guarantees that:
         * - Only this app can trigger the action (explicit intents are not interceptable by others).
         * - The command is queued and processed after [onCreate] completes, so it is never dropped
         *   even if the service is starting up concurrently.
         *
         * The service will hang up all active [PhoneConnection]s, call [ConnectionManager.cleanConnections],
         * and reply with [CallCommandEvent.TearDownComplete].
         */
        fun sendTearDownConnections(context: Context) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.TearDownConnections.action
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendTearDownConnections: startService failed: $e") }
        }

        /**
         * Sends a [ServiceAction.NotifyPending] command with [callId] to this service via [startService].
         *
         * Used when only pending registration is needed without immediately following up with
         * [TelephonyUtils.addNewIncomingCall]. For the incoming call path use
         * [ServiceAction.AddNewIncomingCall] directly (see [startIncomingCall]).
         */
        fun sendNotifyPending(
            context: Context,
            callId: String,
        ) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.NotifyPending.action
                    putExtras(CallMetadata(callId = callId).toBundle())
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendNotifyPending: startService failed for callId=$callId: $e") }
        }

        /**
         * Sends a [ServiceAction.ReserveAnswer] command with [callId] to this service via [startService].
         *
         * Using an explicit [startService] intent guarantees delivery even if the service is still
         * starting up (the intent is queued to [onStartCommand] after [onCreate] completes), which
         * closes the race where a broadcast could be dropped before [commandReceiver] is registered.
         */
        fun sendReserveAnswer(
            context: Context,
            callId: String,
        ) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.ReserveAnswer.action
                    putExtras(CallMetadata(callId = callId).toBundle())
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendReserveAnswer: startService failed for callId=$callId: $e") }
        }

        /**
         * Sends a [ServiceAction.CleanConnections] command to this service via [startService].
         *
         * Using an explicit [startService] intent (instead of a broadcast) prevents external apps
         * from injecting a fake CleanConnections command on API < 33 where broadcast receivers
         * registered without a permission are effectively exported.
         */
        fun sendCleanConnections(context: Context) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.CleanConnections.action
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendCleanConnections: startService failed: $e") }
        }

        /**
         * Sends [ServiceAction.SyncAudioState] to [PhoneConnectionService].
         * The service will call [PhoneConnection.forceUpdateAudioState] on all active connections,
         * which re-emits audio device and mute state broadcasts back to the main process.
         * Used by [ForegroundService.onDelegateSet] to restore Flutter UI after hot restart.
         */
        fun sendSyncAudioState(context: Context) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.SyncAudioState.action
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendSyncAudioState: startService failed: $e") }
        }

        /**
         * Sends [ServiceAction.SyncConnectionState] to [PhoneConnectionService].
         * The service will re-fire [CallLifecycleEvent.AnswerCall] for every connection whose
         * [PhoneConnection.hasAnswered] flag is true. This lets the main process
         * ([ForegroundService]) populate [MainProcessConnectionTracker.connectionStates] even
         * when it starts after the AnswerCall broadcast was originally emitted (cold-start race).
         */
        fun sendSyncConnectionState(context: Context) {
            val intent =
                Intent(context, PhoneConnectionService::class.java).apply {
                    action = ServiceAction.SyncConnectionState.action
                }
            runCatching { context.startService(intent) }
                .onFailure { e -> Log.w(TAG, "sendSyncConnectionState: startService failed: $e") }
        }

        /**
         * Handles new outgoing calls and starts the connection service if the service is not running.
         * For more information on system management of creating connection services,
         * refer to the [Android Telecom Framework Documentation](https://developer.android.com/reference/android/telecom/ConnectionService#implementing-connectionservice).
         *
         * @param metadata The [CallMetadata] for the incoming call.
         */
        @RequiresPermission(Manifest.permission.CALL_PHONE)
        fun startOutgoingCall(
            context: Context,
            metadata: CallMetadata,
        ) {
            Log.i(TAG, "onOutgoingCall, callId: ${metadata.callId}")

            val number =
                metadata.number
                    ?: throw InvalidCallMetadataException(
                        "startOutgoingCall: missing destination number for callId=${metadata.callId}",
                    )
            val uri: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
            val telephonyUtils = TelephonyUtils(context)

            if (telephonyUtils.isEmergencyNumber(number)) {
                Log.i(TAG, "onOutgoingCall, trying to call on emergency number: $number")

                val failureMetadata =
                    FailureMetadata(
                        metadata,
                        "Failed to establish outgoing connection: Emergency number",
                        outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER,
                    )

                throw EmergencyNumberException(failureMetadata)
            } else {
                // If there is already an active call not on hold, we terminate it and start a new one,
                // otherwise, we would encounter an exception when placing the outgoing call.
                connectionManager.getActiveConnection()?.let {
                    Log.i(TAG, "onOutgoingCall, hung up previous call: $it")
                    it.hungUp()
                }

                telephonyUtils.placeOutgoingCall(uri, metadata)
            }
        }

        /**
         * Handles new incoming calls and starts the connection service if the service is not running.
         * For more information on system management of creating connection services,
         * refer to the [Android Telecom Framework Documentation](https://developer.android.com/reference/android/telecom/ConnectionService#implementing-connectionservice).
         *
         * @param metadata The [CallMetadata] for the incoming call.
         */
        fun startIncomingCall(
            context: Context,
            metadata: CallMetadata,
            onSuccess: () -> Unit,
            onError: (PIncomingCallError?) -> Unit,
        ) {
            Log.i(TAG, "startIncomingCall: callId=${metadata.callId}")

            ConnectionManager.validateConnectionAddition(metadata = metadata, onSuccess = {
                // Send AddNewIncomingCall to :callkeep_core so that addPendingForIncomingCall
                // and addNewIncomingCall both run in the same process as destroy(). This
                // guarantees FIFO ordering on the shared Telecom binder connection and prevents
                // onCreateIncomingConnectionFailed on callId reuse after a declined call.
                val intent =
                    Intent(context, PhoneConnectionService::class.java).apply {
                        action = ServiceAction.AddNewIncomingCall.action
                        putExtras(metadata.toBundle())
                    }
                runCatching { context.startService(intent) }
                    .onSuccess { onSuccess() }
                    .onFailure { e ->
                        // startService can fail with IllegalStateException on Android 8+ when
                        // the app is in the background. Remove the pending reservation and
                        // propagate the failure via onError so the Pigeon callback is resolved.
                        Log.e(TAG, "startIncomingCall: startService(AddNewIncomingCall) failed for callId=${metadata.callId}", e)
                        connectionManager.removePending(metadata.callId)
                        onError(PIncomingCallError(PIncomingCallErrorEnum.UNKNOWN))
                    }
            }, onError = { incomingCallError ->
                Log.w(TAG, "Incoming call rejected: ${incomingCallError.value}")
                onError(incomingCallError)
            })
        }

        private fun communicate(
            context: Context,
            action: ServiceAction,
            metadata: CallMetadata?,
        ) {
            val intent = Intent(context, PhoneConnectionService::class.java)
            intent.action = action.action
            metadata?.toBundle()?.let { intent.putExtras(it) }

            try {
                context.startService(intent)
            } catch (e: Exception) {
                val reportDispatcher = ConnectionServicePerformBroadcaster.handle

                // Fallback: failed to start PhoneConnectionService.
                // This may happen if the app is in the background, lacks sufficient permissions,
                // or the system restricts service launches (e.g., background start limitations).
                //
                // To avoid the call hanging indefinitely, we proactively finish the call
                // as "HungUp" to ensure consistent call termination on the UI side.
                reportDispatcher.dispatch(context, CallLifecycleEvent.HungUp, metadata?.toBundle())
                Log.d(TAG, "Failed to start service with action: ${action.name}, error: $e")
            }
        }
    }
}
