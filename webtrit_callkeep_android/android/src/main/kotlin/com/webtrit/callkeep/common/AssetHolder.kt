package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.Log

/**
 * Singleton that provides a process-wide [AssetCacheManager].
 *
 * Call [init] once with any [Context] — from the Flutter plugin binding,
 * from a background service, or from an isolated OS process. Initialization
 * is identical in all cases; no Flutter API is required.
 */
@SuppressLint("StaticFieldLeak")
object AssetHolder {
    private var _assetCacheManager: AssetCacheManager? = null

    val assetCacheManager: AssetCacheManager
        get() = _assetCacheManager
            ?: throw IllegalStateException("AssetHolder is not initialized. Call init() first.")

    @Synchronized
    fun init(context: Context) {
        if (_assetCacheManager == null) {
            _assetCacheManager = AssetCacheManager(context)
        } else {
            Log.i("AssetHolder", "AssetHolder is already initialized.")
        }
    }
}
