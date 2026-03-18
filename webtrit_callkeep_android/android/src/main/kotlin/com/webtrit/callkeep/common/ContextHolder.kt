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
