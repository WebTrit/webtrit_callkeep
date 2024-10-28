package com.webtrit.callkeep.connection

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.*

import com.webtrit.callkeep.FlutterLog
import com.webtrit.callkeep.api.foreground.TelephonyForegroundCallkeepApi
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.common.helpers.TelephonyHelper
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.common.models.FailureMetadata
import com.webtrit.callkeep.common.models.OutgoingFailureType
import com.webtrit.callkeep.common.ContextHolder

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
    private lateinit var sensor: PhoneSensorListener

    override fun onCreate() {
        super.onCreate()
        state = PhoneConnectionConsts()
        sensor = PhoneSensorListener()

        sensor.setSensorHandler {
            state.setNearestState(it)
            upsertProximityWakelock()
        }
    }

    private fun upsertProximityWakelock() {
        val isNear = state.isUserNear()
        val shouldListen = state.shouldListenProximity()
        sensor.upsertProximityWakelock(shouldListen && isNear)

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
            ServiceAction.SendDtmf.action -> onSendDTMF(CallMetadata.fromBundle(intent.extras!!))
            ServiceAction.Speaker.action -> onChangeSpeaker(CallMetadata.fromBundle(intent.extras!!))
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
                "onDeclineCall:: callId: ${metadata.callId}"
            )
            connections[metadata.callId]!!.declineCall()
            addConnectionTerminated(metadata.callId)
            sensor.unListen(applicationContext)

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
            connections[metadata.callId]!!.hungUp()
            addConnectionTerminated(metadata.callId)
            sensor.unListen(applicationContext)
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
            sensor.listen(applicationContext)
            connections[metadata.callId]!!.onAnswer()
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
            sensor.listen(applicationContext)
            connections[metadata.callId]!!.establish()
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
            connections[metadata.callId]!!.changeMuteState(metadata.isMute)
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
            connections[metadata.callId]!!.run {
                if (metadata.isHold) onHold() else onUnhold()
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
            if (metadata.proximityEnabled != null) {
                state.setShouldListenProximity(metadata.proximityEnabled)
                upsertProximityWakelock()
            }
            connections[metadata.callId]!!.run {
                updateData(metadata)
            }
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onUpdateCall ${metadata.callId} exception: $e"
            )
        }
    }

    /**
     * Send a DTMF tone during a call, if a connection with the given [metadata.dtmf] exists.
     *
     * @param metadata The [CallMetadata] containing the DTMF tone and call identifier.
     */
    private fun onSendDTMF(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onSendDTMF, callId: ${metadata.callId}")
            connections[metadata.callId]!!.onPlayDtmfTone(metadata.dtmf ?: return)
        } catch (e: Exception) {
            FlutterLog.e(
                TAG, "onUpdateCall ${metadata.callId} exception: $e"
            )
        }
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
        val metaData = CallMetadata.fromBundle(request.extras)

        val connection = PhoneConnection.createIncomingPhoneConnection(applicationContext, metaData)

        state.setShouldListenProximity(metaData.proximityEnabled ?: false)
        connections[metaData.callId] = connection

        return connection
    }


    /**
     * Handles changes in the speaker state of a call based on the provided metadata.
     *
     * @param metadata The metadata containing information about the call.
     */
    private fun onChangeSpeaker(metadata: CallMetadata) {
        try {
            FlutterLog.i(TAG, "onChangeSpeaker, callId: ${metadata.callId}")
            connections[metadata.callId]!!.changeSpeakerState(metadata.isSpeaker)
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
        val metaData = CallMetadata.fromBundle(request.extras)
        val connection = PhoneConnection.createOutgoingPhoneConnection(applicationContext, metaData)


        state.setShouldListenProximity(metaData.proximityEnabled ?: false)

        connections[metaData.callId] = connection

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

        sensor.unListen(this)
        //TODO: Change the method name to better understand the purpose
        onDetachActivity()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneConnectionService"

        private var connections: MutableMap<String, PhoneConnection> = mutableMapOf()
        private var terminatedConnections: MutableList<String> = mutableListOf()

        fun remove(id: String) {
            connections.remove(id)
        }

        fun getOutgoingConnections(): List<PhoneConnection> {
            return connections.values.filter { it.state == Connection.STATE_DIALING }
        }

        fun getConnections(): List<PhoneConnection> {
            return connections.values.toList()
        }

        fun isConnectionAlreadyExists(id: String): Boolean {
            return connections.containsKey(id)
        }

        fun isConnectionAnswered(id: String): Boolean {
            return connections[id]?.isAnswered() ?: false
        }

        fun isConnectionTerminated(id: String): Boolean {
            return terminatedConnections.contains(id)
        }

        fun isExistsActiveConnection(): Boolean {
            return connections.values.any { it.state == Connection.STATE_ACTIVE }
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
            communicate(context, ServiceAction.SendDtmf, metadata)
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
                TelephonyForegroundCallkeepApi.notifyOutgoingFailure(context, FailureMetadata("Failed to establish outgoing connection: Emergency number", outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER))
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
