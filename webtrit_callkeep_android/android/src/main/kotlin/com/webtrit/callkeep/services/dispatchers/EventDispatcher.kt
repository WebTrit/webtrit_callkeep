package com.webtrit.callkeep.services.dispatchers

import android.app.Service
import android.content.Intent
import android.os.Bundle
import com.webtrit.callkeep.models.ConnectionReport
import com.webtrit.callkeep.common.ContextHolder

internal object EventDispatcher {
    internal interface DispatchHandle {
        fun dispatch(report: ConnectionReport, data: Bundle?)
    }

    internal val handle = object : DispatchHandle {
        override fun dispatch(report: ConnectionReport, data: Bundle?) {
            ContextHolder.context.let { ctx ->
                activeServices.forEach { service ->
                    val intent = Intent(ctx, service).apply {
                        this.action = report.name
                        data?.let { putExtras(it) }
                    }
                    ctx.startService(intent)
                }
            }
        }
    }

    internal fun registerService(service: Class<out Service>) {
        activeServices.add(service)
    }

    internal fun unregisterService(service: Class<out Service>) {
        activeServices.remove(service)
    }

    private val activeServices = mutableSetOf<Class<out Service>>()
}