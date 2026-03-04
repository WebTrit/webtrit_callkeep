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
            Log.i("AssetHolder", "AssetManagerHolder is already initialized.")
        }
    }

    /**
     * Initializes the asset manager for isolated processes (e.g. :callkeep_core) that have no
     * Flutter engine attached. Uses a minimal FlutterAssets implementation that replicates the
     * standard Flutter asset path convention ("flutter_assets/<name>"), allowing
     * FlutterAssetManager to read assets from the APK and cache them to disk without a live engine.
     */
    @Synchronized
    fun initForIsolatedProcess(context: Context) {
        if (_flutterAssetManager != null) return
        val isolatedAssets = object : FlutterAssets {
            override fun getAssetFilePathByName(asset: String) = "flutter_assets/$asset"
            override fun getAssetFilePathByName(asset: String, packageName: String) =
                "flutter_assets/packages/$packageName/$asset"
        }
        _flutterAssetManager = FlutterAssetManager(context, isolatedAssets)
    }
}
