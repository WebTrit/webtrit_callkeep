package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.Log

/**
 * Singleton that provides a process-wide [AssetCacheManager].
 *
 * Call [init] once, supplying a [Context] and an [assetPathResolver] that maps
 * logical asset names to paths understood by [android.content.res.AssetManager].
 * The resolver is the only caller-specific detail — the rest of the class is
 * process-agnostic.
 */
@SuppressLint("StaticFieldLeak")
object AssetHolder {
    private var _assetCacheManager: AssetCacheManager? = null

    val assetCacheManager: AssetCacheManager
        get() = _assetCacheManager
            ?: throw IllegalStateException("AssetHolder is not initialized. Call init() first.")

    @Synchronized
    fun init(context: Context, assetPathResolver: (String) -> String) {
        if (_assetCacheManager == null) {
            _assetCacheManager = AssetCacheManager(context, assetPathResolver)
        } else {
            Log.i("AssetHolder", "AssetHolder is already initialized.")
        }
    }
}
