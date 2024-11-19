package com.webtrit.callkeep.common

import android.util.Log
import com.webtrit.callkeep.PDelegateLogsFlutterApi
import com.webtrit.callkeep.PLogTypeEnum

object Log {
    private const val LOG_TAG = "FlutterLog"

    private var isolateDelegates = mutableListOf<com.webtrit.callkeep.PDelegateLogsFlutterApi>()

    fun add(delegate: com.webtrit.callkeep.PDelegateLogsFlutterApi) {
        Log.d(LOG_TAG, "Add flutter log delegate")
        isolateDelegates.add(delegate)
    }

    fun remove(delegate: PDelegateLogsFlutterApi) {
        Log.d(LOG_TAG, "Remove flutter log delegate")
        isolateDelegates.add(delegate)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        emitLog(PLogTypeEnum.ERROR, tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        emitLog(PLogTypeEnum.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        emitLog(PLogTypeEnum.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.i(tag, message)
        emitLog(PLogTypeEnum.WARN, tag, message)
    }

    private fun emitLog(type: PLogTypeEnum, tag: String, message: String) {
        isolateDelegates.forEach {
            it.onLog(type, tag, message) {}
        }
    }
}
