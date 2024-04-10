package com.webtrit.callkeep.common

import android.content.Context
import android.content.SharedPreferences

/**
 * A delegate for managing SharedPreferences related to incoming and root routes.
 */
object StorageDelegate {
    private const val COMMON_PREFERENCES_KEY = "COMMON_PREFERENCES_KEY"
    private const val FLUTTER_INCOMING_INITIAL_ROUTE = "FLUTTER_INCOMING_INITIAL_ROUTE"
    private const val FLUTTER_ROOT_INITIAL_ROUTE = "FLUTTER_ROOT_INITIAL_ROUTE"
    private const val RINGTONE_PATH_KEY = "RINGTONE_PATH_KEY"

    private var sharedPreferences: SharedPreferences? = null

    /**
     * Initializes the incoming path route in SharedPreferences.
     *
     * @param context The application context.
     * @param route The incoming path route to store.
     */
    fun initIncomingPath(context: Context, route: String) {
        getSharedPreferences(context)?.edit()?.putString(FLUTTER_INCOMING_INITIAL_ROUTE, route)
            ?.apply()
    }

    /**
     * Retrieves the stored incoming path route from SharedPreferences.
     *
     * @param context The application context.
     * @return The stored incoming path route or "/" if not found.
     */
    fun getIncomingPath(context: Context): String {
        return getSharedPreferences(context)?.getString(FLUTTER_INCOMING_INITIAL_ROUTE, "/") ?: "/"
    }

    /**
     * Initializes the root path route in SharedPreferences.
     *
     * @param context The application context.
     * @param route The root path route to store.
     */
    fun initRootPath(context: Context, route: String) {
        getSharedPreferences(context)?.edit()?.putString(FLUTTER_ROOT_INITIAL_ROUTE, route)?.apply()
    }

    /**
     * Retrieves the stored root path route from SharedPreferences.
     *
     * @param context The application context.
     * @return The stored root path route or "/" if not found.
     */
    fun getRootPath(context: Context): String {
        return getSharedPreferences(context)?.getString(FLUTTER_ROOT_INITIAL_ROUTE, "/") ?: "/"
    }


    /**
     * Initializes the ringtone path in SharedPreferences.
     *
     * @param context The application context.
     * @param path The ringtone path to store.
     */
    fun initRingtonePath(context: Context, path: String?) {
        if (path == null) return
        getSharedPreferences(context)?.edit()?.putString(RINGTONE_PATH_KEY, path)?.apply()
    }

    /**
     * Retrieves the stored ringtone path from SharedPreferences.
     *
     * @param context The application context.
     * @return The stored ringtone path or null if not found.
     */
    fun getRingtonePath(context: Context): String? {
        return getSharedPreferences(context)?.getString(RINGTONE_PATH_KEY, null)
    }


    private fun getSharedPreferences(context: Context?): SharedPreferences? {
        if (sharedPreferences == null) {
            sharedPreferences =
                context?.getSharedPreferences(COMMON_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
        return sharedPreferences
    }
}
