package com.webtrit.callkeep.services.services.connection

import android.Manifest
import android.content.BroadcastReceiver
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
    private lateinit var telephonyUtils: TelephonyUtils

    private val dispatcher: ConnectionServicePerformBroadcaster.DispatchHandle =
        ConnectionServicePerformBroadcaster.handle

    /**
     * Receiver for commands sent from the main process to this service.
     * Handles [CallCommandEvent.TearDownConnections], [CallCommandEvent.ReserveAnswer],
     * and [CallCommandEvent.CleanConnections].
     */
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CallCommandEvent.TearDownConnections.name -> handleTearDownConnections()
                CallCommandEvent.ReserveAnswer.name -> {
                    val callId = intent.extras?.getString(com.webtrit.callkeep.common.CallDataConst.CALL_ID)
                    if (callId != null) handleReserveAnswer(callId)
                }
                CallCommandEvent.CleanConnections.name -> handleCleanConnections()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Set the service state to true when the system starts the service.
        isRunning = true
        telephonyUtils = TelephonyUtils(applicationContext)

        // Register receiver for commands from the main process.
        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(
                CallCommandEvent.TearDownConnections,
                CallCommandEvent.ReserveAnswer,
                CallCommandEvent.CleanConnections,
            ),
            baseContext,
            commandReceiver,
            exported = false,
        )

        val activityWakelockManager = ActivityWakelockManager(ActivityHolder)
        val proximitySensorManager =
            ProximitySensorManager(applicationContext, PhoneConnectionConsts());

        phoneConnectionServiceDispatcher = PhoneConnectionServiceDispatcher(
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
    fun performEventHandle(event: ConnectionEvent, data: CallMetadata? = null) {
        Log.i(TAG, "performEventHandle: $event")
        dispatcher.dispatch(baseContext, event, data?.toBundle())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action?.let { ServiceAction.from(it) } ?: run {
            Log.w(TAG, "onStartCommand called with null intent or action, ignoring")
            return START_NOT_STICKY
        }
        val metadata = intent.extras?.let { CallMetadata.fromBundle(it) }

        try {
            phoneConnectionServiceDispatcher.dispatch(action, metadata)
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
        connectionManagerPhoneAccount: PhoneAccountHandle, request: ConnectionRequest
    ): Connection {
        val metadata = CallMetadata.fromBundle(request.extras)

        // Check if a connection with the same call ID already exists.
        // If so, reject the new connection request to prevent conflicts.
        if (connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            // Return a failed connection indicating the line is busy.
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        val connection = PhoneConnection.createOutgoingPhoneConnection(
            applicationContext, ::performEventHandle, metadata, ::disconnectConnection
        )
        connectionManager.addConnection(metadata.callId, connection)
        phoneConnectionServiceDispatcher.dispatchLifecycle(
            ConnectionLifecycleAction.ConnectionCreated, metadata
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
        connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?
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

        // Reject connections for call IDs that are no longer pending.
        // This guards against stale Telecom callbacks that arrive after a tearDown
        // cleared the pending set (e.g., Telecom delivers onCreateIncomingConnection
        // after cleanConnections() ran). Without this check, a zombie connection would
        // be created that can never be torn down by the current session.
        if (!connectionManager.isPending(metadata.callId)) {
            Log.w(TAG, "onCreateIncomingConnection: callId=${metadata.callId} not pending, rejecting")
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.LOCAL))
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
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        val connection = PhoneConnection.createIncomingPhoneConnection(
            applicationContext, ::performEventHandle, metadata, ::disconnectConnection
        )
        connectionManager.addConnection(metadata.callId, connection)
        // The call is no longer "pending" — it now has a live PhoneConnection object.
        // Removing it from pendingCallIds ensures that a subsequent reportNewIncomingCall
        // for the same callId (e.g. main process CallBloc arriving ~6 s after the push
        // isolate already answered the call) skips the pendingCallIds branch in
        // checkAndReservePending and correctly reaches the hasAnswered check, returning
        // CALL_ID_ALREADY_EXISTS_AND_ANSWERED instead of CALL_ID_ALREADY_EXISTS.
        connectionManager.removePending(metadata.callId)

        // Apply a deferred answer if answerCall() was called before this connection was created.
        // This closes the async gap between reportNewIncomingCall() (which returns as soon as
        // addNewIncomingCall() is sent to Telecom) and onCreateIncomingConnection (which fires
        // asynchronously on the binder thread).
        if (connectionManager.consumeAnswer(metadata.callId)) {
            Log.i(TAG, "onCreateIncomingConnection: applying deferred answer for callId=${metadata.callId}")
            connection.onAnswer()
        }

        phoneConnectionServiceDispatcher.dispatchLifecycle(
            ConnectionLifecycleAction.ConnectionCreated, metadata
        )

        startService(Intent(applicationContext, ForegroundService::class.java).apply {
            action = "test"
        })
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
        connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?
    ) {
        val callMetadata = CallMetadata.fromBundleOrNull(request?.extras ?: Bundle.EMPTY)
        val callId = callMetadata?.callId

        // Check before removing: if this callId was pending, the failure was for a real call
        // that should be reported to Flutter as ended (e.g., rejected with BUSY because another
        // incoming call was already ringing). If it was not pending, this is a stale Telecom
        // callback from a previous session (guarded by isPending in onCreateIncomingConnection)
        // and should be silently dropped.
        val wasPending = callId != null && connectionManager.isPending(callId)
        callId?.let { connectionManager.removePending(it) }

        val failureContext = "onCreateIncomingConnectionFailed"
        val failureMessage = "$failureContext: $connectionManagerPhoneAccount $request"

        Log.e(TAG, failureMessage)

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
        connectionManager.reserveAnswer(callId)
    }

    private fun handleCleanConnections() {
        Log.i(TAG, "handleCleanConnections: clearing all connections")
        connectionManager.cleanConnections()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        runCatching {
            ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(baseContext, commandReceiver)
        }.onFailure { /* already unregistered */ }
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

        fun startAnswerCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.AnswerCall, metadata)
        }

        fun startEstablishCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.EstablishCall, metadata)
        }

        fun startUpdateCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.UpdateCall, metadata)
        }

        fun startDeclineCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.DeclineCall, metadata)
        }

        fun startHungUpCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.HungUpCall, metadata)
        }

        fun startSendDtmfCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.SendDTMF, metadata)
        }

        fun startMutingCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.Muting, metadata)
        }

        fun startHoldingCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.Holding, metadata)
        }

        fun startSpeaker(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.Speaker, metadata)
        }

        fun setAudioDevice(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.AudioDeviceSet, metadata)
        }

        fun tearDown(context: Context) {
            communicate(context, ServiceAction.TearDown, null)
        }

        /**
         * Sends [CallCommandEvent.TearDownConnections] broadcast to `:callkeep_core`.
         * The process will hang up all active [PhoneConnection]s, call [ConnectionManager.cleanConnections],
         * and reply with [CallCommandEvent.TearDownComplete].
         */
        fun sendTearDownConnections(context: Context) {
            ConnectionServicePerformBroadcaster.handle.dispatch(context, CallCommandEvent.TearDownConnections)
        }

        /**
         * Sends [CallCommandEvent.ReserveAnswer] broadcast with [callId] to `:callkeep_core`.
         * The process will call [ConnectionManager.reserveAnswer] so the deferred answer is applied
         * when [PhoneConnectionService.onCreateIncomingConnection] fires.
         */
        fun sendReserveAnswer(context: Context, callId: String) {
            val data = Bundle().apply { putString(com.webtrit.callkeep.common.CallDataConst.CALL_ID, callId) }
            ConnectionServicePerformBroadcaster.handle.dispatch(context, CallCommandEvent.ReserveAnswer, data)
        }

        /**
         * Sends [CallCommandEvent.CleanConnections] broadcast to `:callkeep_core`.
         * The process will call [ConnectionManager.cleanConnections] without hanging up individual connections.
         */
        fun sendCleanConnections(context: Context) {
            ConnectionServicePerformBroadcaster.handle.dispatch(context, CallCommandEvent.CleanConnections)
        }

        /**
         * Handles new outgoing calls and starts the connection service if the service is not running.
         * For more information on system management of creating connection services,
         * refer to the [Android Telecom Framework Documentation](https://developer.android.com/reference/android/telecom/ConnectionService#implementing-connectionservice).
         *
         * @param metadata The [CallMetadata] for the incoming call.
         */
        @RequiresPermission(Manifest.permission.CALL_PHONE)
        fun startOutgoingCall(context: Context, metadata: CallMetadata) {
            Log.i(TAG, "onOutgoingCall, callId: ${metadata.callId}")

            val uri: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, metadata.number, null)
            val telephonyUtils = TelephonyUtils(context)

            if (telephonyUtils.isEmergencyNumber(metadata.number)) {
                Log.i(TAG, "onOutgoingCall, trying to call on emergency number: ${metadata.number}")

                val failureMetadata = FailureMetadata(
                    metadata,
                    "Failed to establish outgoing connection: Emergency number",
                    outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER
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
            onError: (PIncomingCallError?) -> Unit
        ) {
            Log.i(TAG, "startIncomingCall: callId=${metadata.callId}")

            ConnectionManager.validateConnectionAddition(metadata = metadata, onSuccess = {
                try {
                    TelephonyUtils(context).addNewIncomingCall(metadata)
                    onSuccess()
                } catch (e: Exception) {
                    // addNewIncomingCall failed (e.g. SecurityException, IllegalArgumentException).
                    // Roll back the pending reservation so future reports for this callId are not
                    // permanently rejected with CALL_ID_ALREADY_EXISTS.
                    Log.e(TAG, "startIncomingCall: addNewIncomingCall failed for callId=${metadata.callId}, rolling back pending", e)
                    connectionManager.removePending(metadata.callId)
                    onError(null)
                }
            }, onError = { incomingCallError ->
                Log.w(TAG, "Incoming call rejected: ${incomingCallError.value}")
                onError(incomingCallError)
            })
        }

        private fun communicate(context: Context, action: ServiceAction, metadata: CallMetadata?) {
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
