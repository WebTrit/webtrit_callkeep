package com.webtrit.callkeep.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import com.webtrit.callkeep.FlutterLog

object Broadcast {
    private const val TAG = "Broadcast sender"

    fun notify(
        context: Context,
        action: String,
        attribute: Bundle? = null,
        throwable: Bundle? = null
    ) {
        FlutterLog.i(TAG, "notify $action $attribute $throwable")

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val intent = Intent(action)
            if (attribute != null) {
                intent.putExtras(attribute)
            }
            if (throwable != null) {
                intent.putExtras(throwable)
            }
            context.sendBroadcast(intent)
        }
    }
}
