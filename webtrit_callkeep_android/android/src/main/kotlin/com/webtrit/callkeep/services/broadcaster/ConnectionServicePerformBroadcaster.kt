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
    DidPushIncomingCall,
    OutgoingFailure,
    IncomingFailure,
    ConnectionNotFound;
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
    ConnectionHolding;
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
    ): IntentFilter {
        return createIntentFilter(events).also { filter ->
            context.registerReceiverCompat(receiver, filter, exported)
        }
    }

    private fun createIntentFilter(events: List<ConnectionEvent>): IntentFilter {
        return IntentFilter().apply { events.forEach { addAction(it.name) } }
    }

    fun unregisterConnectionPerformReceiver(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    interface DispatchHandle {
        fun dispatch(context: Context, report: ConnectionEvent, data: Bundle? = null)
    }

    /**
     * Singleton instance that dispatches connection reports via broadcast.
     */
    val handle: DispatchHandle = object : DispatchHandle {
        override fun dispatch(context: Context, report: ConnectionEvent, data: Bundle?) {
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
