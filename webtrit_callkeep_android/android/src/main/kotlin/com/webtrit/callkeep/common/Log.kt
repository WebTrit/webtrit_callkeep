package com.webtrit.callkeep.common

import android.os.Handler
import android.os.Looper
import android.util.Log as AndroidLog

import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum

/**
 * A logging utility that can be instantiated with a specific tag or used statically.
 */
class Log(private val tag: String) {

    /**
     * Logs an error message using the instance tag.
     */
    fun e(message: String, throwable: Throwable? = null) =
        log(PLogTypeEnum.ERROR, tag, "$message\n$throwable")

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
    fun w(message: String, throwable: Throwable? = null) =
        log(PLogTypeEnum.WARN, tag, "$message\n$throwable")

    companion object {
        private const val GLOBAL_PREFIX = "CK-"
        private var isolateDelegates = mutableListOf<PDelegateLogsFlutterApi>()

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
        private fun log(type: PLogTypeEnum, tag: String, message: String) {
            val prefixedTag = "$GLOBAL_PREFIX$tag"
            if (isolateDelegates.isEmpty()) {
                performSystemLog(type, prefixedTag, message)
            } else {
                dispatchToDelegate(type, prefixedTag, message)
            }
        }

        /**
         * Logs to the standard Android system log.
         */
        private fun performSystemLog(type: PLogTypeEnum, tag: String, message: String) {
            when (type) {
                PLogTypeEnum.DEBUG -> AndroidLog.d(tag, message)
                PLogTypeEnum.INFO -> AndroidLog.i(tag, message)
                PLogTypeEnum.WARN -> AndroidLog.w(tag, message)
                PLogTypeEnum.ERROR -> AndroidLog.e(tag, message)
                PLogTypeEnum.VERBOSE -> AndroidLog.v(tag, message)
            }
        }

        /**
         * Dispatches log events to the main thread for delegate consumption.
         */
        private fun dispatchToDelegate(type: PLogTypeEnum, tag: String, message: String) =
            Handler(Looper.getMainLooper()).post { notifyFirstDelegate(type, tag, message) }

        /**
         * Notifies the primary registered isolate delegate.
         */
        private fun notifyFirstDelegate(type: PLogTypeEnum, tag: String, message: String) =
            isolateDelegates.firstOrNull()?.onLog(type, tag, message) {}

        /**
         * Logs an error message. (Static version)
         */
        @JvmStatic
        fun e(tag: String, message: String, throwable: Throwable? = null) =
            log(PLogTypeEnum.ERROR, tag, "$message\n$throwable")

        /**
         * Logs a debug message. (Static version)
         */
        @JvmStatic
        fun d(tag: String, message: String) = log(PLogTypeEnum.DEBUG, tag, message)

        /**
         * Logs an informational message. (Static version)
         */
        @JvmStatic
        fun i(tag: String, message: String) = log(PLogTypeEnum.INFO, tag, message)

        /**
         * Logs a warning message. (Static version)
         */
        @JvmStatic
        fun w(tag: String, message: String, throwable: Throwable? = null) =
            log(PLogTypeEnum.WARN, tag, "$message\n$throwable")
    }
}
