package com.webtrit.callkeep.common

import android.os.Handler
import android.os.Looper
import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum
import android.util.Log as AndroidLog

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
        // List of delegates that will receive log messages
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
         * Logs a message with the specified log type. (Static version)
         */
        private fun log(type: PLogTypeEnum, tag: String, message: String) {
            if (isolateDelegates.isEmpty()) {
                // If no delegates, log to Android's system log
                when (type) {
                    PLogTypeEnum.DEBUG -> AndroidLog.d(tag, message)
                    PLogTypeEnum.INFO -> AndroidLog.i(tag, message)
                    PLogTypeEnum.WARN -> AndroidLog.w(tag, message)
                    PLogTypeEnum.ERROR -> AndroidLog.e(tag, message)
                    PLogTypeEnum.VERBOSE -> AndroidLog.v(tag, message)
                }
            } else {
                // If delegates exist, send the log to the first delegate
                Handler(Looper.getMainLooper()).post {
                    isolateDelegates.firstOrNull()?.onLog(type, tag, message) {}
                }
            }
        }

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
