package com.webtrit.callkeep.services.broadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.managers.NotificationManager

enum class ConnectionPerform {
    AnswerCall, DeclineCall, HungUp, OngoingCall, AudioMuting, ConnectionHolding, SentDTMF, DidPushIncomingCall, ConnectionHasSpeaker, MissedCall, OutgoingFailure, IncomingFailure, ConnectionNotFound;
}

/**
 * This object is responsible for broadcasting the connection service perform events from the connection service.
 */
object ConnectionServicePerformBroadcaster {
    private val notificationManager = NotificationManager()

    fun registerConnectionPerformReceiver(
        performActions: List<ConnectionPerform>, context: Context, receiver: BroadcastReceiver
    ): IntentFilter {
        return createIntentFilter(performActions).also { filter ->
            context.registerReceiverCompat(receiver, filter)
        }
    }

    private fun createIntentFilter(performActions: List<ConnectionPerform>): IntentFilter {
        return IntentFilter().apply { performActions.forEach { addAction(it.name) } }
    }

    fun unregisterConnectionPerformReceiver(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    interface DispatchHandle {
        fun dispatch(context: Context, report: ConnectionPerform, data: Bundle? = null)
    }

    /**
     * Singleton instance that dispatches connection reports via broadcast.
     */
    val handle: DispatchHandle = object : DispatchHandle {
        override fun dispatch(context: Context, report: ConnectionPerform, data: Bundle?) {
            val appContext = context.applicationContext

            // When connection is not found, we need to cancel the notification if it exists and send action for finishing call
            if (report == ConnectionPerform.ConnectionNotFound) {
                data?.getString(CallDataConst.CALL_ID)?.let {
                    notificationManager.cancelActiveCallNotification(it)
                } ?: notificationManager.tearDown()

                notificationManager.cancelIncomingNotification(true)
                appContext.sendInternalBroadcast(ConnectionPerform.HungUp.name, data)
                return
            }

            appContext.sendInternalBroadcast(report.name, data)
        }
    }
}
