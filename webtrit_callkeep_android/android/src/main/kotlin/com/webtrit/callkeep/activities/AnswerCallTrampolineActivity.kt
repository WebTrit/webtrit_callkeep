package com.webtrit.callkeep.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService

/**
 * Invisible activity behind the Answer action of the incoming-call notification.
 *
 * The answer action used to be a service PendingIntent: the tap started
 * [IncomingCallService], which reported the answer to Telecom, and the resulting
 * `PhoneConnection.onAnswer()` then called `ActivityHolder.start()` to bring the host
 * app up. That second step is a plain `startActivity()` from a background service, and
 * the only reason it ever worked is a background-activity-start token the platform
 * granted along the notification -> foreground-service chain. Android 17 no longer
 * grants it, so the launch is refused with `BAL_BLOCK` and the call is answered with no
 * UI and no way to reach one.
 *
 * The launch privilege belongs to the user's tap, not to us: an activity PendingIntent
 * sent by the system is an explicit background-activity-launch exemption. Targeting this
 * activity keeps the tap and the launch in one step - the system starts this activity,
 * and starting the host app from here is a foreground start that needs no exemption.
 *
 * Only the answer action needs this. Declining requires no UI and keeps targeting the
 * service directly.
 *
 * The activity draws nothing and finishes immediately; `showWhenLocked` lets the tap work
 * straight from a lock-screen notification, and the empty task affinity keeps it out of
 * the host app's task.
 */
class AnswerCallTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent?.extras
        if (extras == null) {
            Log.w(TAG, "onCreate: no call metadata in the intent, nothing to answer")
            finish()
            return
        }

        Log.i(TAG, "onCreate: forwarding the answer and bringing the host app up")

        try {
            startService(
                Intent(this, IncomingCallService::class.java).apply {
                    action = NotificationAction.Answer.action
                    putExtras(extras)
                },
            )
        } catch (e: Exception) {
            // Still bring the host app up: the user asked for the call screen, and reaching
            // it is what gives them a way to answer or hang up by hand.
            Log.e(TAG, "onCreate: failed to forward the answer to IncomingCallService", e)
        }

        ActivityHolder.start(this)

        finish()
    }

    companion object {
        private const val TAG = "AnswerCallTrampolineActivity"
    }
}
