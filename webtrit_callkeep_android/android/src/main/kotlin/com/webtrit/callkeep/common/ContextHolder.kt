package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import io.flutter.Log

/**
 * Singleton object for managing application-specific data.
 */
@SuppressLint("StaticFieldLeak")
object ContextHolder {
    private var applicationContext: Context? = null

    // The package name of the application.
    private val packageName: String
        get() = applicationContext?.packageName
            ?: throw IllegalStateException("ContextHolder is not initialized. Call init() first.")

    /**
     * A unique key generated for the broadcast receivers.
     */
    val appUniqueKey: String by lazy {
        packageName + "_56B952AEEDF2E21364884359565F2_"
    }

    /**
     * Provides the application context safely.
     */
    val context: Context
        get() = applicationContext
            ?: throw IllegalStateException("ContextHolder is not initialized. Call init() first.")

    /**
     * Initializes ContextHolder with the given application context.
     * @param context The application context.
     */
    @Synchronized
    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        } else {
            Log.i("ContextHolder", "ContextHolder is already initialized.")
        }
    }
}
