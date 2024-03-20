package com.webtrit.callkeep.webtrit_callkeep_android.services

import  android.content.Context
import com.webtrit.callkeep.webtrit_callkeep_android.common.models.CallMetadata
import com.webtrit.callkeep.webtrit_callkeep_android.services.notification.ActiveCallNotificationBuilder
import com.webtrit.callkeep.webtrit_callkeep_android.services.notification.IncomingCallNotificationBuilder
import com.webtrit.callkeep.webtrit_callkeep_android.services.notification.MissedCallNotificationBuilder

//TODO: Reorganize this service
class NotificationService(
    context: Context
) {
    private val incomingCallNotificationBuilder = IncomingCallNotificationBuilder(context)
    private val activeCallNotificationBuilder = ActiveCallNotificationBuilder(context)
    private val missedCallNotificationBuilder = MissedCallNotificationBuilder(context)

    fun showIncomingCallNotification(callMetaData: CallMetadata, hasAnswerButton: Boolean = true) {
        incomingCallNotificationBuilder.setMetaData(callMetaData)
        incomingCallNotificationBuilder.setNotificationData(mapOf(IncomingCallNotificationBuilder.NOTIFICATION_DATA_HAS_ANSWER_BUTTON to hasAnswerButton))
        incomingCallNotificationBuilder.show()
    }

    fun showActiveCallNotification(callMetaData: CallMetadata) {
        activeCallNotificationBuilder.setMetaData(callMetaData)
        activeCallNotificationBuilder.show()
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
        activeCallNotificationBuilder.hide()
        incomingCallNotificationBuilder.hide()
    }

    fun cancelIncomingNotification() {
        incomingCallNotificationBuilder.hide()
    }
}
