package com.webtrit.callkeep.common

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum

/**
 * A simplified logging utility that sends logs to delegates or system log if no delegates are registered.
 */
object Log {
    private const val LOG_TAG = "FlutterLog"

    // List of delegates that will receive log messages
    private var isolateDelegates = mutableListOf<PDelegateLogsFlutterApi>()

    /**
     * Adds a delegate to receive log messages.
     *
     * @param delegate The log delegate that will handle log messages.
     */
    fun add(delegate: PDelegateLogsFlutterApi) {
        isolateDelegates.add(delegate)
    }

    /**
     * Removes a delegate from receiving log messages.
     *
     * @param delegate The log delegate to be removed.
     */
    fun remove(delegate: PDelegateLogsFlutterApi) {
        isolateDelegates.remove(delegate)
    }

    /**
     * Logs a message with the specified log type.
     *
     * @param type The log type (ERROR, DEBUG, INFO, WARN).
     * @param tag The tag associated with the log message.
     * @param message The message to be logged.
     */
    private fun log(type: PLogTypeEnum, tag: String, message: String) {
        if (isolateDelegates.isEmpty()) {
            // If no delegates, log to Android's system log
            when (type) {
                PLogTypeEnum.DEBUG -> Log.d(tag, message)
                PLogTypeEnum.INFO -> Log.i(tag, message)
                PLogTypeEnum.WARN -> Log.w(tag, message)
                PLogTypeEnum.ERROR -> Log.e(tag, message)
                PLogTypeEnum.VERBOSE -> Log.v(tag, message)
            }
        } else {
            // If delegates exist, send the log to them
            Handler(Looper.getMainLooper()).post {
                isolateDelegates.forEach {
                    it.onLog(type, tag, message) {}
                }
            }
        }
    }

    /**
     * Logs an error message.
     */
    fun e(tag: String, message: String) = log(PLogTypeEnum.ERROR, tag, message)

    /**
     * Logs a debug message.
     */
    fun d(tag: String, message: String) = log(PLogTypeEnum.DEBUG, tag, message)

    /**
     * Logs an informational message.
     */
    fun i(tag: String, message: String) = log(PLogTypeEnum.INFO, tag, message)

    /**
     * Logs a warning message.
     */
    fun w(tag: String, message: String) = log(PLogTypeEnum.WARN, tag, message)
}
