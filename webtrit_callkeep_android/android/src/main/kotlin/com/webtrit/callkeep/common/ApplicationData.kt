package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle

import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets

/**
 * Singleton object for managing application-specific data.
 */
@SuppressLint("StaticFieldLeak")
object ApplicationData {
    private var currentActivityState: Lifecycle.Event? = null

    private var activity: Activity? = null

    lateinit var flutterAssetManager: FlutterAssetManager

    // The package name of the application.
    private lateinit var packageName: String

    /**
     * A unique key generated for the broadcast receivers.
     */
    val appUniqueKey: String by lazy {
        packageName + "_56B952AEEDF2E21364884359565F2_"
    }

    fun getActivity(): Activity? {
        return activity
    }

    fun attachActivity(activity: Activity?) {
        ApplicationData.activity = activity
    }

    fun detachActivity() {
        activity = null
    }

    fun getActivityState(): Lifecycle.Event? {
        return currentActivityState
    }

    /**
     * Initializes the com.webtrit.callkeep.common.ApplicationData with the given application context.
     * @param context The application context.
     */
    fun init(context: Context, assets: FlutterAssets) {
        this.packageName = context.packageName
        flutterAssetManager = FlutterAssetManager(context, assets)

    }

    fun setCurrentActivityState(event: Lifecycle.Event) {
        currentActivityState = event
    }

    fun isActivityVisible(): Boolean {
        return currentActivityState == Lifecycle.Event.ON_RESUME
    }
}
