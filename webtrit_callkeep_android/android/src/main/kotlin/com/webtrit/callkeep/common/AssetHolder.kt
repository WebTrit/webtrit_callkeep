package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets

/**
 * Singleton object for managing application-specific data.
 */
@SuppressLint("StaticFieldLeak")
object AssetHolder {
    private var _flutterAssetManager: FlutterAssetManager? = null

    val flutterAssetManager: FlutterAssetManager
        get() = _flutterAssetManager
            ?: throw IllegalStateException("AssetHolder is not initialized. Call init() first.")

    @Synchronized
    fun init(context: Context, assets: FlutterAssets) {
        if (_flutterAssetManager == null) {
            _flutterAssetManager = FlutterAssetManager(context, assets)
        } else {
            Log.w("AssetHolder", "AssetManagerHolder is already initialized.")
        }
    }
}
