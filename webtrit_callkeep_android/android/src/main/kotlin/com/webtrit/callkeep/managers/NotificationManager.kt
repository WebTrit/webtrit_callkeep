package com.webtrit.callkeep.managers

import android.content.Intent
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.active_call.ActiveCallService
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallRelease
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService

class NotificationManager() {
    fun showIncomingCallNotification(callMetaData: CallMetadata) {
        IncomingCallService.start(context, callMetaData)
    }

    fun cancelIncomingNotification(answered: Boolean) {
        IncomingCallService.release(
            context,
            if (answered) {
                IncomingCallRelease.IC_RELEASE_WITH_ANSWER
            } else {
                IncomingCallRelease.IC_RELEASE_WITH_DECLINE
            },
        )
    }

    fun showActiveCallNotification(
        id: String,
        callMetaData: CallMetadata,
    ) {
        // Re add to head of the list if already exists to update position on held calls switch
        val existPosition = activeCalls.indexOfFirst { it.callId == id }
        if (existPosition != -1) activeCalls.removeAt(existPosition)
        activeCalls.add(0, callMetaData)

        upsertActiveCallsService()
    }

    fun cancelActiveCallNotification(id: String) {
        val existPosition = activeCalls.indexOfFirst { it.callId == id }
        if (existPosition != -1) activeCalls.removeAt(existPosition)

        upsertActiveCallsService()
    }

    private fun upsertActiveCallsService() {
        if (activeCalls.isNotEmpty()) {
            val activeCallsBundles = activeCalls.map { it.toBundle() }
            val intent = Intent(context, ActiveCallService::class.java)
            intent.putExtra("metadata", ArrayList(activeCallsBundles))
            context.startService(intent)
        } else {
            context.stopService(Intent(context, ActiveCallService::class.java))
        }
    }

    fun tearDown() {
        context.stopService(Intent(context, ActiveCallService::class.java))
        context.stopService(Intent(context, IncomingCallService::class.java))
    }

    companion object {
        const val TAG = "NotificationManager"
        private var activeCalls = mutableListOf<CallMetadata>()
    }
}
