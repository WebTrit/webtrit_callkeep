package com.webtrit.callkeep.common

import android.content.Context
import android.content.SharedPreferences
import com.webtrit.callkeep.common.helpers.JsonHelper
import com.webtrit.callkeep.models.ForegroundCallServiceConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A delegate for managing SharedPreferences related to incoming and root routes.
 */
object StorageDelegate {
    private const val COMMON_PREFERENCES_KEY = "COMMON_PREFERENCES_KEY"
    private const val FLUTTER_INCOMING_INITIAL_ROUTE = "FLUTTER_INCOMING_INITIAL_ROUTE"
    private const val FLUTTER_ROOT_INITIAL_ROUTE = "FLUTTER_ROOT_INITIAL_ROUTE"
    private const val RINGTONE_PATH_KEY = "RINGTONE_PATH_KEY"
    private const val RINGBACK_PATH_KEY = "RINGBACK_PATH_KEY"
    private const val SERVICE_CONFIGURATION_KEY = "SERVICE_CONFIGURATION_KEY"
    private const val CALLBACK_DISPATCHER_KEY = "callbackDispatcher"
    private const val ON_START_HANDLER_KEY = "onStartHandler"
    private const val ON_CHANGED_LIFECYCLE_HANDLER_KEY = "onChangedLifecycleHandler"
    private const val ON_NOTIFICATION_SYNC_KEY = "onNotificationSync"
    private const val SIGNALING_SERVICE_RUNNING = "SIGNALING_SERVICE_RUNNING"

    private var sharedPreferences: SharedPreferences? = null

    /**
     * Initializes the incoming path route in SharedPreferences.
     *
     * @param context The application context.
     * @param route The incoming path route to store.
     */
    fun initIncomingPath(context: Context, route: String) {
        getSharedPreferences(context)?.edit()?.putString(FLUTTER_INCOMING_INITIAL_ROUTE, route)?.apply()
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

    /**
     * Initializes the ringback path in SharedPreferences.
     *
     * @param context The application context.
     * @param path The ringback path to store.
     */
    fun initRingbackPath(context: Context, path: String?) {
        if (path == null) return
        getSharedPreferences(context)?.edit()?.putString(RINGBACK_PATH_KEY, path)?.apply()
    }

    /**
     * Retrieves the stored ringback path from SharedPreferences.
     *
     * @param context The application context.
     * @return The stored ringback path or null if not found.
     */
    fun getRingbackPath(context: Context): String? {
        return getSharedPreferences(context)?.getString(RINGBACK_PATH_KEY, null)
    }

    private fun getSharedPreferences(context: Context?): SharedPreferences? {
        if (sharedPreferences == null) {
            sharedPreferences = context?.getSharedPreferences(COMMON_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
        return sharedPreferences
    }

    fun setServiceConfiguration(context: Context, config: ForegroundCallServiceConfig) {
        val jsonString = Json.encodeToString(config)
        getSharedPreferences(context)?.edit()?.apply {
            putString(SERVICE_CONFIGURATION_KEY, jsonString)
            apply()
        }
    }

    fun getForegroundCallServiceConfiguration(context: Context): ForegroundCallServiceConfig {
        val jsonString = getSharedPreferences(context)?.getString(SERVICE_CONFIGURATION_KEY, null)
        return jsonString?.let {
            JsonHelper.json.decodeFromString<ForegroundCallServiceConfig>(it)
        } ?: ForegroundCallServiceConfig(
            null, null, autoRestartOnTerminate = false, autoStartOnBoot = false
        )
    }

    fun setCallbackDispatcher(context: Context, value: Long) {
        getSharedPreferences(context)?.edit()?.apply {
            putLong(CALLBACK_DISPATCHER_KEY, value)
            apply()
        }
    }

    fun setOnStartHandler(context: Context, value: Long) {
        getSharedPreferences(context)?.edit()?.apply {
            putLong(ON_START_HANDLER_KEY, value)
            apply()
        }
    }

    fun setOnNotificationSync(context: Context, value: Long) {
        getSharedPreferences(context)?.edit()?.apply {
            putLong(ON_NOTIFICATION_SYNC_KEY, value)
            apply()
        }
    }

    fun setOnChangedLifecycleHandler(context: Context, value: Long) {
        getSharedPreferences(context)?.edit()?.apply {
            putLong(ON_CHANGED_LIFECYCLE_HANDLER_KEY, value)
            apply()
        }
    }

    fun getCallbackDispatcher(context: Context): Long {
        return getSharedPreferences(context)?.getLong(CALLBACK_DISPATCHER_KEY, -1)
            ?: throw Exception("CallbackDispatcher not found")
    }

    fun getOnStartHandler(context: Context): Long {
        return getSharedPreferences(context)?.getLong(ON_START_HANDLER_KEY, -1)
            ?: throw Exception("OnStartHandler not found")
    }

    fun getOnChangedLifecycleHandler(context: Context): Long {
        return getSharedPreferences(context)?.getLong(ON_CHANGED_LIFECYCLE_HANDLER_KEY, -1)
            ?: throw Exception("OnChangedLifecycleHandler not found")
    }

    fun getOnNotificationSync(context: Context): Long {
        return getSharedPreferences(context)?.getLong(ON_NOTIFICATION_SYNC_KEY, -1)
            ?: throw Exception("OnNotificationSync not found")
    }

    object SignalingService {
        fun setRunning(context: Context, value: Boolean) {
            getSharedPreferences(context)?.edit()?.apply {
                putBoolean(SIGNALING_SERVICE_RUNNING, value)
                apply()
            }
        }

        fun isRunning(context: Context): Boolean {
            return getSharedPreferences(context)?.getBoolean(SIGNALING_SERVICE_RUNNING, false) == true
        }
    }
}
