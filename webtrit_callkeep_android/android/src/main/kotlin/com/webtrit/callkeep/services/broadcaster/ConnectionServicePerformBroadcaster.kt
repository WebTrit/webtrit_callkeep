package com.webtrit.callkeep.services.broadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.managers.NotificationManager

/**
 * Marker interface for all events dispatched between [PhoneConnectionService] and the main process.
 *
 * Implemented by [CallLifecycleEvent] and [CallMediaEvent].
 * The [name] property is provided by each enum and used as the broadcast action string.
 */
sealed interface ConnectionEvent {
    val name: String
}

/**
 * Events that represent call lifecycle transitions reported by [PhoneConnectionService].
 *
 * These are emitted when the state of a call connection changes — a call is answered,
 * hung up, failed to connect, etc.
 */
enum class CallLifecycleEvent : ConnectionEvent {
    AnswerCall,
    DeclineCall,
    HungUp,
    OngoingCall,
    // An incoming PhoneConnection was created in :callkeep_core (onCreateIncomingConnection). The
    // main process registers it in the shadow state and then notifies the Flutter delegate via the
    // public didPushIncomingCall callback. Named after the cross-process fact (a connection was
    // reported), NOT the public callback -- the handler does register + deliver, not just deliver.
    IncomingConnectionReported,
    // Carries the authoritative connection state (CallMetadata.connectionState) so the main process
    // can MIRROR it into the shadow state, instead of inferring a fixed state per event type. Emitted
    // from PhoneConnection.onStateChanged (Telecom) / StandaloneCallService transitions. Live states
    // only -- terminal DISCONNECTED stays on the cause-carrying HungUp/DeclineCall events.
    ConnectionStateChanged,

    // Re-delivery of a still-ringing incoming call to a freshly-attached Flutter delegate
    // (e.g. after a push->foreground isolate handoff or hot restart). Unlike IncomingConnectionReported,
    // this is NOT gated by the signaling-registered suppression: the new delegate has no record
    // of the call and must be seeded before it processes signaling events. Emitted by
    // PhoneConnectionService.handleReplayConnectionStates for connections in STATE_RINGING.
    ReEmitIncomingCall,
    OutgoingFailure,
    IncomingFailure,
    ConnectionNotFound,
}

/**
 * Events that represent audio/media state changes on an active call, reported by [PhoneConnectionService].
 *
 * These are emitted when mute state, hold state, DTMF, or audio device selection changes.
 */
enum class CallMediaEvent : ConnectionEvent {
    AudioMuting,
    AudioDeviceSet,
    AudioDevicesUpdate,
    SentDTMF,
    ConnectionHolding,
}

/**
 * Commands sent from the main process to the `:callkeep_core` process (or received back as acks).
 *
 * Direction:
 * - [TearDownConnections] — Main -> `:callkeep_core`: call [PhoneConnection.hungUp] on all
 *   active connections and [ConnectionManager.cleanConnections].
 * - [TearDownComplete]    — `:callkeep_core` -> Main: ack that [TearDownConnections] completed.
 * - [ReserveAnswer]       — Main -> `:callkeep_core`: deferred answer reservation for a callId
 *   before its [PhoneConnection] is created (payload: [com.webtrit.callkeep.common.CallDataConst.CALL_ID]).
 * - [CleanConnections]    — Main -> `:callkeep_core`: clear all connections without [PhoneConnection.hungUp].
 *
 * Using a dedicated enum (rather than adding to [CallLifecycleEvent]) makes the IPC direction
 * explicit and keeps report events separate from command events.
 */
enum class CallCommandEvent : ConnectionEvent {
    TearDownConnections,
    TearDownComplete,
    ReserveAnswer,
    CleanConnections,
}

/**
 * This object is responsible for broadcasting the connection service perform events from the connection service.
 */
object ConnectionServicePerformBroadcaster {
    private val notificationManager = NotificationManager()

    fun registerConnectionPerformReceiver(
        events: List<ConnectionEvent>,
        context: Context,
        receiver: BroadcastReceiver,
        exported: Boolean = true,
    ): IntentFilter =
        createIntentFilter(events).also { filter ->
            context.registerReceiverCompat(receiver, filter, exported)
        }

    private fun createIntentFilter(events: List<ConnectionEvent>): IntentFilter = IntentFilter().apply { events.forEach { addAction(it.name) } }

    fun unregisterConnectionPerformReceiver(
        context: Context,
        receiver: BroadcastReceiver,
    ) {
        context.unregisterReceiver(receiver)
    }

    interface DispatchHandle {
        fun dispatch(
            context: Context,
            report: ConnectionEvent,
            data: Bundle? = null,
        )
    }

    /**
     * Singleton instance that dispatches connection reports via broadcast.
     */
    val handle: DispatchHandle =
        object : DispatchHandle {
            override fun dispatch(
                context: Context,
                report: ConnectionEvent,
                data: Bundle?,
            ) {
                val appContext = context.applicationContext

                // When connection is not found, cancel any visible notification and synthesise a HungUp
                // so that subscribers waiting for a termination event are not left hanging.
                if (report == CallLifecycleEvent.ConnectionNotFound) {
                    data?.getString(CallDataConst.CALL_ID)?.let {
                        notificationManager.cancelActiveCallNotification(it)
                    } ?: notificationManager.tearDown()

                    notificationManager.cancelIncomingNotification(true)
                    appContext.sendInternalBroadcast(CallLifecycleEvent.HungUp.name, data)
                    return
                }

                appContext.sendInternalBroadcast(report.name, data)
            }
        }
}
