package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets

/**
 * Singleton object for managing application-specific data.
 */
@SuppressLint("StaticFieldLeak")
object ContextHolder {
    lateinit var flutterAssetManager: FlutterAssetManager

    // The package name of the application.
    private lateinit var packageName: String

    private const val TAG = "ApplicationData"

    /**
     * A unique key generated for the broadcast receivers.
     */
    val appUniqueKey: String by lazy {
        packageName + "_56B952AEEDF2E21364884359565F2_"
    }

    /**
     * Initializes the com.webtrit.callkeep.common.ApplicationData with the given application context.
     * @param context The application context.
     */
    fun init(context: Context, assets: FlutterAssets) {
        this.packageName = context.packageName
        flutterAssetManager = FlutterAssetManager(context, assets)
    }
}
