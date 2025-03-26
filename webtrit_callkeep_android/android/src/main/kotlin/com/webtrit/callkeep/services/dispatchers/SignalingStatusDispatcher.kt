package com.webtrit.callkeep.services.dispatchers

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import com.webtrit.callkeep.common.ContextHolder.context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus

@SuppressLint("StaticFieldLeak")
object SignalingStatusDispatcher {
    private const val TAG = "SignalingHolder"

    const val ACTION_STATUS_CHANGED = "SIGNALING_STATUS_CHANGED"

    private var status: SignalingStatus? = null
    val currentStatus: SignalingStatus?
        get() = status

    private val registeredServices = mutableSetOf<Class<out Service>>()

    fun setStatus(newStatus: SignalingStatus) {
        status = newStatus
        notifyStatusChanged(newStatus)
    }

    fun registerService(service: Class<out Service>) {
        registeredServices.add(service)
    }

    fun unregisterService(service: Class<out Service>) {
        registeredServices.remove(service)
    }

    private fun notifyStatusChanged(status: SignalingStatus) {
        for (service in registeredServices) {
            val intent = Intent(context, service).apply {
                action = ACTION_STATUS_CHANGED
                putExtras(status.toBundle())
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.i(TAG, "Failed to start service: $e service: $service")
            }
        }
    }
}
