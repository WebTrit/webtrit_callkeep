package com.webtrit.callkeep.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.services.services.connection.StandaloneCallService

/**
 * Trampoline for the Answer action of the standalone incoming-call notification.
 *
 * Android 12+ blocks activity starts from a service that was launched by a notification
 * action (notification trampoline restriction). When the Answer PendingIntent targeted
 * [StandaloneCallService] directly, the ActivityHolder.start() call inside its answer
 * handler was rejected with "Indirect notification activity start (trampoline) blocked"
 * whenever the app was in the background. An activity PendingIntent is exempt from that
 * restriction, so the notification launches this invisible activity instead: it forwards
 * the answer command to [StandaloneCallService], brings the host app to the front (legal,
 * because this activity itself is now the foreground) and finishes immediately.
 */
class StandaloneAnswerTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forward =
            Intent(this, StandaloneCallService::class.java).apply {
                action = intent.action
                intent.extras?.let { putExtras(it) }
            }
        try {
            startService(forward)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: failed to forward '${intent.action}' to StandaloneCallService", e)
        }
        ActivityHolder.start(this)
        finish()
    }

    companion object {
        private const val TAG = "StandaloneAnswerTrampolineActivity"
    }
}
