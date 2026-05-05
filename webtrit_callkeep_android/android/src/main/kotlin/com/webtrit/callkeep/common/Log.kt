package com.webtrit.callkeep.common

import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log as AndroidLog

/**
 * A logging utility that can be instantiated with a specific tag or used statically.
 */
class Log(
    private val tag: String,
) {
    fun e(
        message: String,
        throwable: Throwable? = null,
    ) = log(PLogTypeEnum.ERROR, tag, message, throwable)

    fun d(message: String) = log(PLogTypeEnum.DEBUG, tag, message)

    fun i(message: String) = log(PLogTypeEnum.INFO, tag, message)

    fun v(message: String) = log(PLogTypeEnum.VERBOSE, tag, message)

    fun w(
        message: String,
        throwable: Throwable? = null,
    ) = log(PLogTypeEnum.WARN, tag, message, throwable)

    companion object {
        private const val GLOBAL_PREFIX = "WebtritCallkeep"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        @Volatile
        private var logFilePath: String? = null

        fun setLogFilePath(path: String) {
            logFilePath = path
            AndroidLog.d(GLOBAL_PREFIX, "setLogFilePath: $path")
        }

        fun initFromContext(context: android.content.Context) {
            val path = StorageDelegate.Logging.getLogFilePath(context)
            AndroidLog.d(GLOBAL_PREFIX, "initFromContext: getLogFilePath=$path pid=${android.os.Process.myPid()}")
            if (path != null) {
                logFilePath = path
            }
        }

        /** No-op: kept for API compatibility until delegate API is removed. */
        @JvmStatic
        fun add(delegate: PDelegateLogsFlutterApi) = Unit

        /** No-op: kept for API compatibility until delegate API is removed. */
        @JvmStatic
        fun remove(delegate: PDelegateLogsFlutterApi) = Unit

        private fun log(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) {
            val prefixedTag = "$GLOBAL_PREFIX.$tag"
            writeToFile(type, prefixedTag, message, throwable)
            performSystemLog(type, prefixedTag, message, throwable)
        }

        private fun nativeLogFilePath(): String? = logFilePath?.let { if (it.endsWith(".log")) it.dropLast(4) + "_native.log" else "$it.native" }

        @Synchronized
        private fun writeToFile(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            val path = nativeLogFilePath() ?: return
            try {
                val file = File(path)
                LogFileRotator.rotateIfNeeded(file)
                val level =
                    when (type) {
                        PLogTypeEnum.DEBUG -> "D"
                        PLogTypeEnum.INFO -> "I"
                        PLogTypeEnum.WARN -> "W"
                        PLogTypeEnum.ERROR -> "E"
                        PLogTypeEnum.VERBOSE -> "V"
                    }
                val timestamp = dateFormat.format(Date())
                val line =
                    if (throwable != null) {
                        "$timestamp $level $tag: $message\n${AndroidLog.getStackTraceString(throwable)}\n"
                    } else {
                        "$timestamp $level $tag: $message\n"
                    }
                val bytes = line.toByteArray(Charsets.UTF_8)
                FileOutputStream(file, true).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                    fos.fd.sync()
                }
            } catch (e: Exception) {
                AndroidLog.e(GLOBAL_PREFIX, "writeToFile failed for $path: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun performSystemLog(
            type: PLogTypeEnum,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            val msg = "$tag: $message"
            when (type) {
                PLogTypeEnum.DEBUG -> AndroidLog.d(GLOBAL_PREFIX, msg, throwable)
                PLogTypeEnum.INFO -> AndroidLog.i(GLOBAL_PREFIX, msg, throwable)
                PLogTypeEnum.WARN -> AndroidLog.w(GLOBAL_PREFIX, msg, throwable)
                PLogTypeEnum.ERROR -> AndroidLog.e(GLOBAL_PREFIX, msg, throwable)
                PLogTypeEnum.VERBOSE -> AndroidLog.v(GLOBAL_PREFIX, msg, throwable)
            }
        }

        @JvmStatic
        fun e(
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) = log(PLogTypeEnum.ERROR, tag, message, throwable)

        @JvmStatic
        fun d(
            tag: String,
            message: String,
        ) = log(PLogTypeEnum.DEBUG, tag, message)

        @JvmStatic
        fun i(
            tag: String,
            message: String,
        ) = log(PLogTypeEnum.INFO, tag, message)

        @JvmStatic
        fun w(
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) = log(PLogTypeEnum.WARN, tag, message, throwable)
    }
}
