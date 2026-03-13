package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.Log

/**
 * Singleton that provides a process-wide [FlutterAssetManager].
 *
 * Call [init] once with any [Context] — from the Flutter plugin binding,
 * from a background service, or from an isolated OS process. There is no
 * dependency on the Flutter plugin API, so initialization is identical
 * regardless of whether a FlutterEngine is present.
 */
@SuppressLint("StaticFieldLeak")
object AssetHolder {
    private var _flutterAssetManager: FlutterAssetManager? = null

    val flutterAssetManager: FlutterAssetManager
        get() = _flutterAssetManager
            ?: throw IllegalStateException("AssetHolder is not initialized. Call init() first.")

    @Synchronized
    fun init(context: Context) {
        if (_flutterAssetManager == null) {
            _flutterAssetManager = FlutterAssetManager(context)
        } else {
            Log.i("AssetHolder", "AssetManagerHolder is already initialized.")
        }
    }
}
