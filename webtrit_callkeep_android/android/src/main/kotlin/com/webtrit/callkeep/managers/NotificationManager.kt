package com.webtrit.callkeep.managers

import android.app.Notification
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.MissedCallNotificationBuilder
import com.webtrit.callkeep.services.ActiveCallService
import com.webtrit.callkeep.services.IncomingCallService
import io.flutter.Log

class NotificationManager() {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }
    private val missedCallNotificationBuilder by lazy { MissedCallNotificationBuilder() }

    fun showIncomingCallNotification(callMetaData: CallMetadata, hasAnswerButton: Boolean = true) {
        IncomingCallService.start(context, callMetaData)
    }

    fun cancelIncomingNotification() {
        IncomingCallService.stop(context)
    }

    fun showMissedCallNotification(callMetaData: CallMetadata) {
        val notification = missedCallNotificationBuilder.apply {
            setCallMetaData(callMetaData)
        }.build()
        val id = callMetaData.number.hashCode()

        showRegularNotification(notification, id)
    }

    fun cancelMissedCall(callMetaData: CallMetadata) {
        cancelRegularNotification(callMetaData.number.hashCode())
    }

    fun showActiveCallNotification(id: String, callMetaData: CallMetadata) {
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

    private fun showRegularNotification(notification: Notification, id: Int) {
        if (!notificationManager.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled")
            return
        }

        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notifications exception", e)
        }
    }

    private fun cancelRegularNotification(id: Int) {
        notificationManager.cancel(id)
    }

    companion object {
        const val TAG = "NotificationManager"
        private var activeCalls = mutableListOf<CallMetadata>()
    }
}
