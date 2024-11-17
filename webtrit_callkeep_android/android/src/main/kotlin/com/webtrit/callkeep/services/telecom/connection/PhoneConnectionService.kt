package com.webtrit.callkeep.services.telecom.connection

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.*
import com.webtrit.callkeep.FlutterLog
import com.webtrit.callkeep.api.foreground.TelephonyForegroundCallkeepApi
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.common.helpers.TelephonyHelper
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.FailureMetadata
import com.webtrit.callkeep.models.OutgoingFailureType
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.managers.ProximitySensorManager
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

/**
 * `PhoneConnectionService` is a service class responsible for managing phone call connections
 * in the Webtrit CallKeep Android library. It handles incoming and outgoing calls,
 * call actions (answer, decline, mute, hold, etc.), and provides methods for interacting with
 * phone call connections.
 *
 * @constructor Creates a new instance of `PhoneConnectionService`.
 */
class PhoneConnectionService : ConnectionService() {
    private lateinit var state: PhoneConnectionConsts
    private lateinit var sensorManager: ProximitySensorManager

    override fun onCreate() {
        super.onCreate()
        sensorManager = ProximitySensorManager(applicationContext, PhoneConnectionConsts())
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            // Avoid using onStartCommand for creating outgoing calls as the service may not be functioning properly.
            // The system is responsible for managing service creation and destruction.
            // Use the static method `outgoingCall` of PhoneConnectionService to create outgoing calls.
            ServiceAction.OutgoingCall.action -> {}

            // Avoid using onStartCommand for creating incoming calls as the service may not be functioning properly.
            // The system is responsible for managing service creation and destruction.
            // Use the static method `incomingCall` of PhoneConnectionService to create incoming calls.
            ServiceAction.IncomingCall.action -> {}

            ServiceAction.HungUpCall.action -> onHungUpCall(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.DeclineCall.action -> onDeclineCall(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.AnswerCall.action -> onAnswerCall(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.EstablishCall.action -> onEstablishCall(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.Muting.action -> onChangeMute(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.Holding.action -> onChangeHold(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.UpdateCall.action -> onUpdateCall(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.SendDTMF.action -> onSendDTMF(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.Speaker.action -> onChangeSpeaker(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.TearDown.action -> tearDown();
            ServiceAction.DetachActivity.action -> onDetachActivity()
        }
        return START_NOT_STICKY
    }

    /**
     * This function is called when the current activity is being detached or destroyed.
     * It iterates through all outgoing connections and invokes the `onDisconnect` method
     * for each of them. This is typically used to clean up resources and gracefully
     * disconnect from external services or components before the activity is destroyed.
     *
     * @see Connection.onDisconnect
     */
    private fun onDetachActivity() {
        getConnections().forEach {
            FlutterLog.i(TAG, "onDetachActivity, disconnect outgoing call, callId: ${it.id}")
            it.onDisconnect()
        }
    }

    /**
     * Declines an incoming call associated with the provided `CallMetadata`.
     * If a connection with the specified identifier exists, the `declineCall` method
     * of that connection is invoked to decline the call.
     *
     * Additionally, this function unregisters the sensor listener within the application context.
     *
     * @param metadata The metadata associated with the call to be declined.
     *
     */
    private fun onDeclineCall(metadata: CallMetadata) {
        try {
            FlutterLog.i(
                TAG,
                "onDeclineCall:: callId: ${metadata.callId} isActivityVisible: ${ActivityHolder.isActivityVisible()} currentActivityState: ${ActivityHolder.getActivityState()} connections: "
            )
            // The connection might be null, for example, if multiple notification receivers attempt to decline the call simultaneously.
            // Ensure the connection exists before proceeding to decline call the call.
            getConnection(metadata.callId)?.declineCall()
            addConnectionTerminated(metadata.callId)
            sensorManager.stopListening()
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onDeclineCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Handles hanging up a call based on the provided [CallMetadata].
     *
     * If a valid connection is found in the [connections] map, it initiates the hang-up process.
     * If no connection is found, it triggers a callback and logs a workaround to handle cases where the connection is not available.
     *
     * @param metadata The call metadata containing the identifier.
     */
    private fun onHungUpCall(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onHungUpCall, callId: ${metadata.callId}")
            // The connection might be null, for example, if multiple notification receivers attempt to decline the call simultaneously.
            // Ensure the connection exists before proceeding to hang up the call.
            getConnection(metadata.callId)?.hungUp()
            addConnectionTerminated(metadata.callId)
            sensorManager.stopListening()
        } catch (e: Exception) {
            // WORKAROUND:
            // Sometimes it happens that the connection is no longer available when the user tries to end the call,
            // so in order for the logic to work, a callback is triggered.
            // TODO: Investigate and address the root cause of missing connections.
            TelephonyForegroundCallkeepApi.notifyDeclineCall(applicationContext, metadata)
            FlutterLog.e(
                TAG, "onDeclineCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Accepts an incoming call associated with the provided `CallMetadata`.
     * This function registers a sensor listener within the application context,
     * allowing for certain sensor-based call handling features.
     *
     * If a connection with the specified identifier exists, the `answer` method
     * of that connection is invoked to accept the call.
     *
     * @param metadata The metadata associated with the call to be answered.
     *
     * @see PhoneConnection.answer
     */
    private fun onAnswerCall(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onAnswerCall, callId: ${metadata.callId}")
            sensorManager.startListening()
            getConnection(metadata.callId)?.onAnswer()
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onAnswerCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Processes the action of picking up an incoming call.
     * This function registers a sensor listener within the application context,
     * enabling certain sensor-based call handling features.
     *
     * If a connection with the specified identifier exists, the `сallPickup` method
     * of that connection is invoked to indicate that the call has been picked up.
     *
     * @param metadata The metadata associated with the call being picked up.
     *
     * @see PhoneConnection.establish
     */
    private fun onEstablishCall(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onEstablishCall, callId: ${metadata.callId}")
            sensorManager.startListening()
            getConnection(metadata.callId)?.establish()
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onEstablishCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Changes the mute state of a call associated with the provided `CallMetadata`.
     * If a connection with the specified identifier exists, the `changeMuteState` method
     * of that connection is invoked to toggle the mute state of the call.
     *
     * @param metadata The metadata associated with the call for which the mute state is changed.
     *
     */
    private fun onChangeMute(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onChangeMute, callId: ${metadata.callId}")
            getConnection(metadata.callId)?.changeMuteState(metadata.hasMute)
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onChangeMute ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Perform an action based on the hold status of a call associated with [metadata].
     *
     * This function checks the hold status in [metadata] and invokes the appropriate
     * action on the corresponding connection if it exists.
     *
     * @param metadata The [CallMetadata] containing information about the call.
     */
    private fun onChangeHold(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onChangeHold, callId: ${metadata.callId}")
            getConnection(metadata.callId)?.run {
                if (metadata.hasHold) onHold() else onUnhold()
            }
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onChangeHold ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Update the state and metadata for a call based on the provided [metadata].
     *
     * This function is called when an action to update a call's metadata is received.
     * It updates the video state and connection data for the call.
     *
     * @param metadata The [CallMetadata] containing updated call information.
     */
    private fun onUpdateCall(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onUpdateCall, callId: ${metadata.callId}")

            sensorManager.updateProximityWakelock()

            getConnection(metadata.callId)?.updateData(metadata)
        } catch (e: Exception) {
            FlutterLog.e(TAG, "onUpdateCall ${metadata.callId} exception: $e")
        }
    }

    /**
     * Send a DTMF tone during a call, if a connection with the given DTMF exists.
     *
     * @param metadata The [CallMetadata] containing the DTMF tone and call identifier.
     */
    private fun onSendDTMF(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onSendDTMF, callId: ${metadata.callId}")
            getConnection(metadata.callId)?.onPlayDtmfTone(metadata.dualToneMultiFrequency ?: return)
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onUpdateCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Clean connection service resources.
     */
    private fun tearDown() {
        getConnections().forEach {
            it.hungUp()
            cancelTimeout(it.id)
        }
        cleanConnectionTerminated();
    }

    /**
     * Handles changes in the speaker state of a call based on the provided metadata.
     *
     * @param metadata The metadata containing information about the call.
     */
    private fun onChangeSpeaker(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onChangeSpeaker, callId: ${metadata.callId}")
            getConnection(metadata.callId)?.changeSpeakerState(metadata.hasSpeaker)
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onChangeSpeaker ${metadata.callId} exception: $e"
            )
        }
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

        synchronized(connectionResourceLock) {
            // Check if a connection with the same call ID already exists.
            // If so, reject the new connection request to prevent conflicts.
            if (isConnectionAlreadyExists(metadata.callId)) {
                // Return a failed connection indicating the line is busy.
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
            }

            val connection = PhoneConnection.createOutgoingPhoneConnection(applicationContext, metadata)
            state.setShouldListenProximity(metadata.proximityEnabled)
            addConnection(
                metadata.callId, connection, TIMEOUT_DURATION_MS, DEFAULT_OUTGOING_STATES
            ) {
                connection.hungUp()
            }
            return connection
        }
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
        FlutterLog.e(
            TAG, "onCreateOutgoingConnectionFailed: $connectionManagerPhoneAccount  $request"
        )

        TelephonyForegroundCallkeepApi.notifyOutgoingFailure(
            applicationContext,
            FailureMetadata("onCreateOutgoingConnectionFailed: $connectionManagerPhoneAccount  $request")
        )
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

        synchronized(connectionResourceLock) {
            // Check if a connection with the same ID already exists.
            // This can occur if receivers from both the activity and the service
            // trigger the incoming call flow simultaneously.
            if (isConnectionAlreadyExists(metadata.callId)) {
                // Return a failed connection indicating an error.
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
            }

            // Check if there is already an existing incoming connection.
            // If so, decline the new incoming connection to prevent conflicts in initializing the incoming call flow.
            if (isExistsIncomingConnection()) {
                // Return a failed connection indicating the line is busy.
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.BUSY))
            }

            val connection = PhoneConnection.createIncomingPhoneConnection(applicationContext, metadata)
            addConnection(
                metadata.callId, connection, TIMEOUT_DURATION_MS, DEFAULT_INCOMING_STATES
            ) {
                connection.hungUp()
            }
            return connection
        }
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
        FlutterLog.e(
            TAG,
            "onCreateIncomingConnectionFailed:: $connectionManagerPhoneAccount  $request connections: ${getConnections().map { it.toString() }} "
        )
        TelephonyForegroundCallkeepApi.notifyIncomingFailure(
            applicationContext, FailureMetadata("On create incoming connection failed")
        )
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    override fun onDestroy() {
        FlutterLog.i(TAG, "onDestroy")
        sensorManager.stopListening()
        //TODO: Change the method name to better understand the purpose
        onDetachActivity()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneConnectionService"
        private const val TIMEOUT_DURATION_MS = 60_000L

        val DEFAULT_INCOMING_STATES = listOf(Connection.STATE_NEW, Connection.STATE_RINGING)
        val DEFAULT_OUTGOING_STATES = listOf(Connection.STATE_DIALING)

        private val connections: ConcurrentHashMap<String, PhoneConnection> = ConcurrentHashMap()
        private val connectionTimers: ConcurrentHashMap<String, Runnable> = ConcurrentHashMap()

        private var terminatedConnections: MutableList<String> = mutableListOf()

        private val connectionResourceLock = Any()

        fun cancelTimeout(callId: String) {
            connectionTimers.remove(callId)?.let {
                FlutterLog.i(TAG, "Timeout canceled for callId: $callId")
            }
        }

        private fun startTimeout(
            callId: String, timeout: Long, validStates: List<Int> = DEFAULT_INCOMING_STATES, onTimeout: () -> Unit
        ) {
            val mainHandler = Handler(Looper.getMainLooper())

            val timerTask = Runnable {
                synchronized(connectionResourceLock) {
                    val connection = connections[callId]
                    if (connection != null && connection.state in validStates) {
                        mainHandler.post {
                            FlutterLog.i(TAG, "Timeout reached for callId: $callId. Ending call.")
                            onTimeout()
                        }
                        remove(callId)
                    }
                }
            }

            connectionTimers[callId] = timerTask

            Timer().schedule(object : TimerTask() {
                override fun run() = timerTask.run()
            }, timeout)
        }

        @Synchronized
        fun addConnection(
            callId: String,
            connection: PhoneConnection,
            timeout: Long? = null,
            validStates: List<Int> = DEFAULT_INCOMING_STATES,
            onTimeout: (() -> Unit)? = null
        ) {
            if (!connections.containsKey(callId)) {
                connections[callId] = connection

                if (timeout != null && onTimeout != null) {
                    startTimeout(callId, timeout, validStates, onTimeout)
                }
            }
        }

        fun isExistsActiveConnection(): Boolean {
            synchronized(connectionResourceLock) {
                return connections.values.any { it.state == Connection.STATE_ACTIVE }
            }
        }

        fun isExistsIncomingConnection(): Boolean {
            return connections.values.any { it.state == Connection.STATE_NEW || it.state == Connection.STATE_RINGING }
        }

        fun getActiveOrPendingConnection(): PhoneConnection? {
            return connections.values.find { it.state == Connection.STATE_NEW || it.state == Connection.STATE_RINGING || it.state == Connection.STATE_ACTIVE }
        }

        fun getConnection(id: String): PhoneConnection? {
            return connections.values.find { it.id == id }
        }

        fun remove(id: String) {
            synchronized(connectionResourceLock) {
                connections.remove(id)
            }
        }

        fun getConnections(): List<PhoneConnection> {
            return connections.values.toList()
        }

        fun isConnectionAlreadyExists(id: String): Boolean {
            return connections.containsKey(id)
        }

        fun isConnectionAnswered(id: String): Boolean {
            return connections[id]?.isAnswered() == true
        }

        fun isConnectionTerminated(id: String): Boolean {
            return terminatedConnections.contains(id)
        }

        fun getActiveConnection(): PhoneConnection? {
            for (connection in connections.values) {
                if (connection.state == Connection.STATE_ACTIVE) return connection
            }
            return null
        }

        private fun addConnectionTerminated(id: String) {
            terminatedConnections.add(id)
        }

        fun cleanConnectionTerminated() {
            terminatedConnections.clear()
        }

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

        fun notifyAboutDetachActivity(context: Context) {
            communicate(context, ServiceAction.DetachActivity, null)
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
            FlutterLog.i(TAG, "onOutgoingCall, callId: ${metadata.callId}")

            val uri: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, metadata.number, null)
            val account = Telecom.getPhoneAccountHandle(context)

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, metadata.toBundle())
            }
            if (TelephonyHelper(context).isEmergencyNumber(metadata.number)) {
                FlutterLog.i(TAG, "onOutgoingCall, trying to call on emergency number: ${metadata.number}")
                TelephonyForegroundCallkeepApi.notifyOutgoingFailure(
                    context, FailureMetadata(
                        "Failed to establish outgoing connection: Emergency number",
                        outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER
                    )
                )
            } else {
                // If there is already an active call not on hold, we terminate it and start a new one,
                // otherwise, we would encounter an exception when placing the outgoing call.
                getActiveConnection()?.let {
                    FlutterLog.i(TAG, "onOutgoingCall, hung up previous call: $it")
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
            FlutterLog.i(TAG, "onIncomingCall, callId: ${metadata.callId}")

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
