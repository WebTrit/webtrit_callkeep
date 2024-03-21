package com.webtrit.callkeep.webtrit_callkeep_android

import android.util.Log

object FlutterLog {
    private const val LOG_TAG = "FlutterLog"

    private var isolateDelegates = mutableListOf<PDelegateLogsFlutterApi>()

    fun add(delegate: PDelegateLogsFlutterApi) {
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

    private fun emitLog(type: PLogTypeEnum, tag: String, message: String) {
        isolateDelegates.forEach {
            it.onLog(type, tag, message) {}
        }
    }
}
