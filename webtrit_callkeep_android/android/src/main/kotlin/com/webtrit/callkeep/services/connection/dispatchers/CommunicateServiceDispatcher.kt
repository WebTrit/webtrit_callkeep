package com.webtrit.callkeep.services.connection.dispatchers

import android.app.Service
import android.content.Intent
import android.os.Bundle
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.ConnectionReport

/**
 * A helper object that dispatches [com.webtrit.callkeep.models.ConnectionReport] events from
 * [com.webtrit.callkeep.services.connection.connection.PhoneConnectionService] and
 * [com.webtrit.callkeep.services.connection.connection.PhoneConnection] to registered Android [android.app.Service] components.
 *
 * This helper encapsulates the logic of dispatching events to registered services in the application,
 * instead of managing it directly within the PhoneConnectionService-related classes.
 *
 * Typically, there are two services involved:
 * - [com.webtrit.callkeep.services.foreground.ForegroundService], which represents the connection with the activity and shares its lifecycle.
 * - [com.webtrit.callkeep.services.IncomingCallService], which represents the connection with the notification in a background isolate.
 *
 * Services registered with this dispatcher will receive dispatched events and have established method channels
 * for communication with the Flutter side.
 *
 * Services must be registered via [registerService] to receive dispatched events.
 * Each registered service will be started with an intent containing the report name as the action,
 * and optional extras as a bundle.
 *
 * Services used with this dispatcher must be annotated with @Keep to avoid being removed or renamed
 * during code shrinking or obfuscation.
 * Ensure your Proguard or R8 configuration keeps these service classes and their constructors.
 */
internal object CommunicateServiceDispatcher {
    private const val TAG = "CommunicateServiceDispatcher"

    internal interface DispatchHandle {
        fun dispatch(report: ConnectionReport, data: Bundle?)
    }

    internal val handle = object : DispatchHandle {
        override fun dispatch(report: ConnectionReport, data: Bundle?) {
            ContextHolder.context.let { context ->
                activeServices.forEach { service ->
                    val intent = Intent(context, service).apply {
                        this.action = report.name
                        data?.let { putExtras(it) }
                    }

                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        Log.i(TAG, "Failed to start service: $e service: $service")
                    }
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