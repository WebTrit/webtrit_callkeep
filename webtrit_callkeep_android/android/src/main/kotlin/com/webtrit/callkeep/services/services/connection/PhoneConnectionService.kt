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
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.TelephonyUtils
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.EmergencyNumberException
import com.webtrit.callkeep.models.FailureMetadata
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
        // Initialize AssetCacheManager for the :callkeep_core process so that
        // PhoneConnection.onShowIncomingCallUi() can resolve the custom ringtone asset
        // path via AssetCacheManager.getAsset(). Without this, AssetCacheManager.getAsset()
        // may throw IllegalStateException, which is caught inside getRingtone() and causes
        // a fallback to the system default ringtone.
        AssetCacheManager.init(applicationContext)
        // Set the service state to true when the system starts the service.
        isRunning = true

        val activityWakelockManager = ActivityWakelockManager(ActivityHolder)
        val proximitySensorManager =
            ProximitySensorManager(applicationContext, PhoneConnectionConsts())

        phoneConnectionServiceDispatcher =
            PhoneConnectionServiceDispatcher(
                connectionManager,
                ::performEventHandle,
                activityWakelockManager,
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
        val action =
            intent?.action?.let { ServiceAction.from(it) } ?: run {
                Log.w(TAG, "onStartCommand: unknown or missing action '${intent?.action}', ignoring")
                return START_NOT_STICKY
            }
        val metadata = intent.extras?.let { CallMetadata.fromBundle(it) }

        try {
            when (action) {
                // IPC commands from the main process — handled directly, not routed through the
                // call-connection dispatcher. Using startService (instead of broadcasts) guarantees
                // delivery even if the service is starting up: the intent is queued and processed
                // after onCreate() completes, so these handlers are always reachable.
                ServiceAction.TearDownConnections -> {
                    handleTearDownConnections()
                }

                ServiceAction.ReserveAnswer -> {
                    metadata?.callId?.let { handleReserveAnswer(it) }
                        ?: Log.w(TAG, "onStartCommand: ReserveAnswer missing callId")
                }

                ServiceAction.NotifyPending -> {
                    metadata?.callId?.let { handleNotifyPending(it) }
                        ?: Log.w(TAG, "onStartCommand: NotifyPending missing callId")
                }

                ServiceAction.CleanConnections -> {
                    handleCleanConnections()
                }

                ServiceAction.SyncAudioState -> {
                    handleSyncAudioState()
                }

                ServiceAction.SyncConnectionState -> {
                    handleSyncConnectionState()
                }

                else -> {
                    phoneConnectionServiceDispatcher.dispatch(action, metadata)
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
        val metadata = CallMetadata.fromBundle(request.extras)

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
        val metadata = CallMetadata.fromBundle(request.extras)
        Log.i(TAG, "onCreateIncomingConnection: entry callId=${metadata.callId} account=$connectionManagerPhoneAccount")

        // Guard against stale Telecom callbacks that arrive after a tearDown.
        //
        // Both onCreateIncomingConnection (posted to this service's main-thread handler by the
        // Telecom binder stub) and handleNotifyPending (posted via startService -> onStartCommand)
        // run on the same main thread, but their relative arrival order is non-deterministic:
        // Telecom's dispatch path through TelecomManager is independent of ActivityManager's
        // startService delivery, so onCreateIncomingConnection can fire before isPending is true.
        //
        // Strategy:
        //   - isPending == true  : normal path, NotifyPending arrived first (common case).
        //   - isPending == false AND isForcedTerminated : stale post-tearDown callback — reject.
        //   - isPending == false AND NOT isForcedTerminated : NotifyPending IPC delayed (race
        //     window) — register as pending now so the rest of the flow sees consistent state.
        if (!connectionManager.isPending(metadata.callId)) {
            if (connectionManager.isForcedTerminated(metadata.callId)) {
                Log.w(TAG, "onCreateIncomingConnection: callId=${metadata.callId} force-terminated by tearDown, rejecting stale callback")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.LOCAL))
            }
            // NotifyPending IPC has not arrived yet — register as pending now.
            // handleNotifyPending will call addPendingForIncomingCall later (idempotent).
            Log.d(TAG, "onCreateIncomingConnection: callId=${metadata.callId} not pending yet, accepting (NotifyPending race window)")
            connectionManager.addPendingForIncomingCall(metadata.callId)
        }

        // Check if a connection with the same ID already exists.
        // This can occur if receivers from both the activity and the service
        // trigger the incoming call flow simultaneously.
        if (connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            // Clean up pending state to avoid leaks — returning a failed Connection
            // does NOT trigger onCreateIncomingConnectionFailed.
            connectionManager.removePending(metadata.callId)
            connectionManager.consumeAnswer(metadata.callId)
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }

        // Check if there is already an existing incoming connection.
        // If so, decline the new incoming connection to prevent conflicts in initializing the incoming call flow.
        if (connectionManager.isExistsIncomingConnection()) {
            // Clean up pending state to avoid leaks — returning a failed Connection
            // does NOT trigger onCreateIncomingConnectionFailed.
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
        // Note: pendingCallIds in :callkeep_core is now populated via NotifyPending IPC, so this
        // check correctly distinguishes legitimate failures from stale callbacks.
        val wasPending = callId != null && connectionManager.isPending(callId)
        callId?.let { connectionManager.removePending(it) }

        val failureContext = "onCreateIncomingConnectionFailed"
        val failureMessage = "$failureContext: callId=$callId wasPending=$wasPending account=$connectionManagerPhoneAccount"

        Log.e(TAG, "$failureMessage — Telecom rejected the incoming call registration")

        if (wasPending && callId != null) {
            // Notify Flutter that this call ended so it can clean up its call state.
            Log.i(TAG, "onCreateIncomingConnectionFailed: firing HungUp for pending callId=$callId")
            dispatcher.dispatch(baseContext, CallLifecycleEvent.HungUp, CallMetadata(callId = callId).toBundle())
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
         * Must be called from the main process before [TelephonyUtils.addNewIncomingCall] so that
         * :callkeep_core's [ConnectionManager.pendingCallIds] is populated before Telecom triggers
         * [onCreateIncomingConnection]. This bridges the dual-process gap: [checkAndReservePending]
         * runs in the main-process JVM (a different [ConnectionManager] instance), so :callkeep_core
         * never sees those entries without this explicit IPC notification.
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

            val uri: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, metadata.number, null)
            val telephonyUtils = TelephonyUtils(context)

            if (telephonyUtils.isEmergencyNumber(metadata.number)) {
                Log.i(TAG, "onOutgoingCall, trying to call on emergency number: ${metadata.number}")

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
                try {
                    // Notify :callkeep_core's ConnectionManager about the pending callId before
                    // calling addNewIncomingCall. This ensures that onCreateIncomingConnection's
                    // isPending gate accepts the connection: checkAndReservePending ran in the
                    // main-process JVM (a different ConnectionManager instance), so we must
                    // explicitly tell :callkeep_core via IPC.
                    sendNotifyPending(context, metadata.callId)
                    TelephonyUtils(context).addNewIncomingCall(metadata)
                    onSuccess()
                } catch (e: Exception) {
                    // addNewIncomingCall failed (e.g. SecurityException, IllegalArgumentException).
                    // Roll back the pending reservation so future reports for this callId are not
                    // permanently rejected with CALL_ID_ALREADY_EXISTS.
                    Log.e(
                        TAG,
                        "startIncomingCall: addNewIncomingCall failed for callId=${metadata.callId}, rolling back pending",
                        e,
                    )
                    connectionManager.removePending(metadata.callId)
                    onError(null)
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
