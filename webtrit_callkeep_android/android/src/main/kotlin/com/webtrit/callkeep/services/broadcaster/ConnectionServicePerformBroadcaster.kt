package com.webtrit.callkeep.services.broadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import com.webtrit.callkeep.common.registerReceiverCompat
import com.webtrit.callkeep.common.sendInternalBroadcast

enum class ConnectionPerform {
    AnswerCall, DeclineCall, HungUp, OngoingCall, AudioMuting, ConnectionHolding, SentDTMF, DidPushIncomingCall, ConnectionHasSpeaker, MissedCall, OutgoingFailure, IncomingFailure;
}

object ConnectionServicePerformBroadcaster {
    const val TAG = "ConnectionServicePerformBroadcaster"

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
            context.applicationContext.sendInternalBroadcast(report.name, data)
        }
    }
}
