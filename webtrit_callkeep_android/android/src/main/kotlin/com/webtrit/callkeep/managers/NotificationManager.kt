package com.webtrit.callkeep.managers

import  android.content.Context
import android.content.Intent
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

    fun showActiveCallNotification(callMetaData: CallMetadata) {
        val intent = Intent(context, ActiveCallService::class.java)
        callMetaData.toBundle().let { intent.putExtra("metadata",it) }
        context.startService(intent)
    }

    fun showMissedCallNotification(callMetaData: CallMetadata) {
        missedCallNotificationBuilder.setMetaData(callMetaData)
        missedCallNotificationBuilder.show()
    }

    fun cancelMissedCall(callMetaData: CallMetadata) {
        missedCallNotificationBuilder.setMetaData(callMetaData)
        missedCallNotificationBuilder.cancel()
    }

    fun cancelActiveNotification() {
        context.stopService(Intent(context, ActiveCallService::class.java))
        incomingCallNotificationBuilder.hide()
    }

    fun cancelIncomingNotification() {
        incomingCallNotificationBuilder.hide()
    }
}
