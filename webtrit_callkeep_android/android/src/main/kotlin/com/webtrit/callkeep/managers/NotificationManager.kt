package com.webtrit.callkeep.managers

import  android.content.Context
import android.content.Intent
import android.os.Bundle
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.ActiveCallNotificationBuilder
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import com.webtrit.callkeep.notifications.MissedCallNotificationBuilder
import com.webtrit.callkeep.services.ActiveCallService

//TODO: Reorganize this service
//TODO: refactor notification handling to track multiple active lines + outgoing calls maybe?

class NotificationManager(
    context: Context
) {
    private val incomingCallNotificationBuilder = IncomingCallNotificationBuilder(context)
    private val missedCallNotificationBuilder = MissedCallNotificationBuilder(context)

    fun showIncomingCallNotification(callMetaData: CallMetadata, hasAnswerButton: Boolean = true) {
        incomingCallNotificationBuilder.setMetaData(callMetaData)
        incomingCallNotificationBuilder.setNotificationData(mapOf(IncomingCallNotificationBuilder.NOTIFICATION_DATA_HAS_ANSWER_BUTTON to hasAnswerButton))
        incomingCallNotificationBuilder.show()
    }

    // TODO: rename
    fun showActiveCallNotification(id: String, callMetaData: CallMetadata) {
        activeCalls[id] = callMetaData
        upsertActiveCallsService()
    }

    fun showMissedCallNotification(callMetaData: CallMetadata) {
        missedCallNotificationBuilder.setMetaData(callMetaData)
        missedCallNotificationBuilder.show()
    }

    fun cancelMissedCall(callMetaData: CallMetadata) {
        missedCallNotificationBuilder.setMetaData(callMetaData)
        missedCallNotificationBuilder.cancel()
    }

    // TODO: rename
    fun cancelActiveCallNotification(id: String) {
        activeCalls.remove(id)
        upsertActiveCallsService()
    }

    fun cancelIncomingNotification() {
        incomingCallNotificationBuilder.hide()
    }

    private fun upsertActiveCallsService() {
        if (activeCalls.isNotEmpty()) {
            val activeCallsBundles = activeCalls.map { it.value.toBundle() }
            val intent = Intent(context, ActiveCallService::class.java)
            intent.putExtra("metadata", ArrayList(activeCallsBundles))
            context.startService(intent)
        } else {
            context.stopService(Intent(context, ActiveCallService::class.java))
        }
    }

    companion object {
        const val TAG = "NotificationManager"
        var activeCalls = mutableMapOf<String, CallMetadata>()
    }
}
