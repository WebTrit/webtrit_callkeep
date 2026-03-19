package com.webtrit.callkeep.common

import android.os.Handler
import android.os.Looper
import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum
import java.util.concurrent.CopyOnWriteArrayList
import android.util.Log as AndroidLog

/**
 * A logging utility that can be instantiated with a specific tag or used statically.
 */
class Log(private val tag: String) {
    /**
     * Logs an error message using the instance tag.
     */
    fun e(
        message: String,
        throwable: Throwable? = null,
    ) = log(PLogTypeEnum.ERROR, tag, "$message\n$throwable")

    /**
     * Logs a debug message using the instance tag.
     */
    fun d(message: String) = log(PLogTypeEnum.DEBUG, tag, message)

    /**
     * Logs an informational message using the instance tag.
     */
    fun i(message: String) = log(PLogTypeEnum.INFO, tag, message)

    /**
     * Logs a verbose message using the instance tag.
     */
    fun v(message: String) = log(PLogTypeEnum.VERBOSE, tag, message)

    /**
     * Logs a warning message using the instance tag.
     */
    fun w(
        message: String,
        throwable: Throwable? = null,
    ) = log(PLogTypeEnum.WARN, tag, "$message\n$throwable")

    companion object {
        /**
         * Prefix prepended to all log tags to uniquely identify library-related log output.
         */
        private const val GLOBAL_PREFIX = "CK-"

        /**
         * Collection of registered Flutter API delegates that receive and process log events.
         */
        private var isolateDelegates = CopyOnWriteArrayList<PDelegateLogsFlutterApi>()

        /**
         * Reusable handler tied to the main looper for dispatching logs to delegates.
         */
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Adds a delegate to receive log messages.
         */
        @JvmStatic
        fun add(delegate: PDelegateLogsFlutterApi) {
            isolateDelegates.add(delegate)
        }

        /**
         * Removes a delegate from receiving log messages.
         */
        @JvmStatic
        fun remove(delegate: PDelegateLogsFlutterApi) {
            isolateDelegates.remove(delegate)
        }

        /**
         * Internal dispatcher for log messages.
         */
        private fun log(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) {
            val prefixedTag = "$GLOBAL_PREFIX$tag"
            if (isolateDelegates.isEmpty()) {
                performSystemLog(type, prefixedTag, message, throwable)
            } else {
                dispatchToDelegate(type, prefixedTag, message, throwable)
            }
        }

        /**
         * Logs to the standard Android system log with proper throwable handling.
         */
        private fun performSystemLog(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            when (type) {
                PLogTypeEnum.DEBUG -> AndroidLog.d(tag, message, throwable)
                PLogTypeEnum.INFO -> AndroidLog.i(tag, message, throwable)
                PLogTypeEnum.WARN -> AndroidLog.w(tag, message, throwable)
                PLogTypeEnum.ERROR -> AndroidLog.e(tag, message, throwable)
                PLogTypeEnum.VERBOSE -> AndroidLog.v(tag, message, throwable)
            }
        }

        /**
         * Dispatches log events to the main thread for delegate consumption.
         */
        private fun dispatchToDelegate(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = mainHandler.post { notifyFirstDelegate(type, tag, message, throwable) }

        /**
         * Notifies the primary registered isolate delegate.
         */
        private fun notifyFirstDelegate(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            val fullMessage =
                if (throwable != null) {
                    "$message\n${AndroidLog.getStackTraceString(throwable)}"
                } else {
                    message
                }
            isolateDelegates.firstOrNull()?.onLog(type, tag, fullMessage) {}
        }

        /**
         * Logs an error message. (Static version)
         */
        @JvmStatic
        fun e(
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) = log(PLogTypeEnum.ERROR, tag, "$message\n$throwable")

        /**
         * Logs a debug message. (Static version)
         */
        @JvmStatic
        fun d(
            tag: String,
            message: String,
        ) = log(PLogTypeEnum.DEBUG, tag, message)

        /**
         * Logs an informational message. (Static version)
         */
        @JvmStatic
        fun i(
            tag: String,
            message: String,
        ) = log(PLogTypeEnum.INFO, tag, message)

        /**
         * Logs a warning message. (Static version)
         */
        @JvmStatic
        fun w(
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) = log(PLogTypeEnum.WARN, tag, "$message\n$throwable")
    }
}
