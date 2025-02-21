package com.webtrit.callkeep.services.telecom.connection

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.api.foreground.TelephonyForegroundCallkeepApi
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.common.helpers.TelephonyHelper
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType

/**
 * `PhoneConnectionService` is a service class responsible for managing phone call connections
 * in the Webtrit CallKeep Android library. It handles incoming and outgoing calls,
 * call actions (answer, decline, mute, hold, etc.), and provides methods for interacting with
 * phone call connections.
 *
 * @constructor Creates a new instance of `PhoneConnectionService`.
 */
class PhoneConnectionService : ConnectionService() {
    private lateinit var sensorManager: ProximitySensorManager
    private lateinit var activityWakelockManager: ActivityWakelockManager
    private lateinit var phoneConnectionServiceDispatcher: PhoneConnectionServiceDispatcher

    override fun onCreate() {
        super.onCreate()
        sensorManager = ProximitySensorManager(applicationContext, PhoneConnectionConsts())
        activityWakelockManager = ActivityWakelockManager(ActivityHolder)
        phoneConnectionServiceDispatcher = PhoneConnectionServiceDispatcher(connectionManager, sensorManager)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = ServiceAction.from(intent.action)
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

        val connection =
            PhoneConnection.createOutgoingPhoneConnection(applicationContext, metadata, ::disconnectConnection)
        sensorManager.setShouldListenProximity(metadata.proximityEnabled)
        connectionManager.addConnection(metadata.callId, connection)

        if (metadata.hasVideo) {
            activityWakelockManager.acquireScreenWakeLock()
        }

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
        Log.e(
            TAG, "onCreateOutgoingConnectionFailed: $connectionManagerPhoneAccount  $request"
        )

        TelephonyForegroundCallkeepApi.notifyOutgoingFailure(
            applicationContext,
            FailureMetadata("onCreateOutgoingConnectionFailed: $connectionManagerPhoneAccount  $request")
        )

        if (!connectionManager.hasVideoConnections()) {
            activityWakelockManager.releaseScreenWakeLock()
        }

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

        // Check if a connection with the same ID already exists.
        // This can occur if receivers from both the activity and the service
        // trigger the incoming call flow simultaneously.
        if (connectionManager.isConnectionAlreadyExists(metadata.callId)) {
            // Return a failed connection indicating an error.
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }

        // Check if there is already an existing incoming connection.
        // If so, decline the new incoming connection to prevent conflicts in initializing the incoming call flow.
        if (connectionManager.isExistsIncomingConnection()) {
            // Return a failed connection indicating the line is busy.
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
        }

        val connection =
            PhoneConnection.createIncomingPhoneConnection(applicationContext, metadata, ::disconnectConnection)
        sensorManager.setShouldListenProximity(true)
        connectionManager.addConnection(metadata.callId, connection)

        if (metadata.hasVideo) {
            activityWakelockManager.acquireScreenWakeLock()
        }

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
        Log.e(
            TAG, "onCreateIncomingConnectionFailed:: $connectionManagerPhoneAccount  $connectionManager "
        )
        TelephonyForegroundCallkeepApi.notifyIncomingFailure(
            applicationContext, FailureMetadata("On create incoming connection failed")
        )

        if (!connectionManager.hasVideoConnections()) {
            activityWakelockManager.releaseScreenWakeLock()
        }

        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    private fun disconnectConnection(connection: PhoneConnection) {
        Log.e(TAG, "disconnectConnection:: $connection")

        if (!connectionManager.hasVideoConnections()) {
            activityWakelockManager.releaseScreenWakeLock()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        sensorManager.stopListening()
        activityWakelockManager.dispose()
        connectionManager.getConnections().forEach {
            Log.i(TAG, "onDetachActivity, disconnect outgoing call, callId: ${it.id}")
            it.onDisconnect()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneConnectionService"

        var connectionManager: ConnectionManager = ConnectionManager()

        fun startIncomingCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.IncomingCall, metadata)
        }

        fun startOutgoingCall(context: Context, metadata: CallMetadata) {
            communicate(context, ServiceAction.OutgoingCall, metadata)
        }

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

        fun tearDown(context: Context) {
            communicate(context, ServiceAction.TearDown, null)
        }

        /**
         * Handles new outgoing calls and starts the connection service if the service is not running.
         * For more information on system management of creating connection services,
         * refer to the [Android Telecom Framework Documentation](https://developer.android.com/reference/android/telecom/ConnectionService#implementing-connectionservice).
         *
         * @param metadata The [CallMetadata] for the incoming call.
         */
        @SuppressLint("MissingPermission")
        private fun outgoingCall(context: Context, metadata: CallMetadata) {
            Log.i(TAG, "onOutgoingCall, callId: ${metadata.callId}")

            val uri: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, metadata.number, null)
            val account = Telecom.getPhoneAccountHandle(context)

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, metadata.toBundle())
            }
            if (TelephonyHelper(context).isEmergencyNumber(metadata.number)) {
                Log.i(TAG, "onOutgoingCall, trying to call on emergency number: ${metadata.number}")
                TelephonyForegroundCallkeepApi.notifyOutgoingFailure(
                    context, FailureMetadata(
                        "Failed to establish outgoing connection: Emergency number",
                        outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER
                    )
                )
            } else {
                // If there is already an active call not on hold, we terminate it and start a new one,
                // otherwise, we would encounter an exception when placing the outgoing call.
                connectionManager.getActiveConnection()?.let {
                    Log.i(TAG, "onOutgoingCall, hung up previous call: $it")
                    it.hungUp()
                }

                Telecom.getTelecomManager(context).placeCall(uri, extras)
            }
        }

        /**
         * Handles new incoming calls and starts the connection service if the service is not running.
         * For more information on system management of creating connection services,
         * refer to the [Android Telecom Framework Documentation](https://developer.android.com/reference/android/telecom/ConnectionService#implementing-connectionservice).
         *
         * @param metadata The [CallMetadata] for the incoming call.
         */
        private fun incomingCall(context: Context, metadata: CallMetadata) {
            Log.i(TAG, "onIncomingCall, callId: ${metadata.callId}")

            val telecomManager = Telecom.getTelecomManager(context)
            val account = Telecom.getPhoneAccountHandle(context)

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                putBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_RINGING, true)
                putAll(metadata.toBundle())
            }

            telecomManager.addNewIncomingCall(account, extras)
        }

        private fun communicate(context: Context, action: ServiceAction, metadata: CallMetadata?) {
            when (action) {
                ServiceAction.IncomingCall -> {
                    incomingCall(context, metadata!!)
                }

                ServiceAction.OutgoingCall -> {
                    outgoingCall(context, metadata!!)
                }

                else -> {
                    val intent = Intent(context, PhoneConnectionService::class.java)
                    intent.action = action.action
                    metadata?.toBundle()?.let { intent.putExtras(it) }
                    context.startService(intent)
                }
            }
        }
    }
}
