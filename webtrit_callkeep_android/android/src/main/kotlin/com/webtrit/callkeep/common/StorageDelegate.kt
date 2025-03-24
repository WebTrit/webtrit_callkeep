package com.webtrit.callkeep.common

import android.content.Context
import android.content.SharedPreferences
import com.webtrit.callkeep.R
import com.webtrit.callkeep.common.StorageDelegate.SignalingService.ON_START_HANDLER

/**
 * A delegate for managing SharedPreferences related to incoming and root routes.
 */
object StorageDelegate {
    private const val COMMON_PREFERENCES = "COMMON_PREFERENCES"

    private var sharedPreferences: SharedPreferences? = null

    private fun getSharedPreferences(context: Context?): SharedPreferences? {
        if (sharedPreferences == null) {
            sharedPreferences = context?.getSharedPreferences(COMMON_PREFERENCES, Context.MODE_PRIVATE)
        }
        return sharedPreferences
    }

    object Sound {
        private const val RINGTONE_PATH = "RINGTONE_PATH_KEY"
        private const val RINGBACK_PATH = "RINGBACK_PATH_KEY"

        fun initRingtonePath(context: Context, path: String?) {
            if (path == null) return
            getSharedPreferences(context)?.edit()?.apply { putString(RINGTONE_PATH, path).apply() }
        }

        fun getRingtonePath(context: Context): String? {
            return getSharedPreferences(context)?.getString(RINGTONE_PATH, null)
        }

        fun initRingbackPath(context: Context, path: String?) {
            if (path == null) return
            getSharedPreferences(context)?.edit()?.apply { putString(RINGBACK_PATH, path)?.apply() }
        }

        fun getRingbackPath(context: Context): String? {
            return getSharedPreferences(context)?.getString(RINGBACK_PATH, null)
        }
    }

    object IncomingCallService {
        private const val ON_NOTIFICATION_SYNC = "ON_NOTIFICATION_SYNC"
        private const val INCOMING_CALL_HANDLER = "INCOMING_CALL_HANDLER"
        private const val LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN =
            "LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN"

        fun setLaunchBackgroundIsolateEvenIfAppIsOpen(context: Context, value: Boolean) {
            getSharedPreferences(context)?.edit()?.apply {
                putBoolean(LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN, value)
                apply()
            }
        }

        fun isLaunchBackgroundIsolateEvenIfAppIsOpen(context: Context): Boolean {
            return getSharedPreferences(context)?.getBoolean(
                LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN, false
            ) == true
        }

        fun setOnNotificationSync(context: Context, value: Long) {
            getSharedPreferences(context)?.edit()?.apply {
                putLong(ON_NOTIFICATION_SYNC, value)
                apply()
            }
        }

        fun getOnNotificationSync(context: Context): Long {
            return getSharedPreferences(context)?.getLong(ON_NOTIFICATION_SYNC, -1)
                ?: throw Exception("OnNotificationSync not found")
        }

        fun setCallbackDispatcher(context: Context, value: Long) {
            getSharedPreferences(context)?.edit()?.apply {
                putLong(INCOMING_CALL_HANDLER, value)
                apply()
            }
        }

        fun getCallbackDispatcher(context: Context): Long {
            return getSharedPreferences(context)?.getLong(INCOMING_CALL_HANDLER, -1)
                ?: throw Exception("INCOMING_CALL_HANDLER not found")
        }
    }

    object SignalingService {
        private const val SIGNALING_SERVICE_ENABLED = "SIGNALING_SERVICE_ENABLED"

        private const val SS_NOTIFICATION_TITLE_KEY = "SS_NOTIFICATION_TITLE_KEY"
        private const val SS_NOTIFICATION_DESCRIPTION_KEY = "SS_NOTIFICATION_DESCRIPTION_KEY"

        private const val ON_START_HANDLER = "ON_START_HANDLER"
        private const val ON_LIFECYCLE_CHANGE_HANDLER = "ON_LIFECYCLE_CHANGE_HANDLER"

        private const val CALLBACK_DISPATCHER = "CALLBACK_DISPATCHER"

        fun setSignalingServiceEnabled(context: Context, value: Boolean) {
            getSharedPreferences(context)?.edit()?.apply {
                putBoolean(SIGNALING_SERVICE_ENABLED, value)
                apply()
            }
        }

        fun isSignalingServiceEnabled(context: Context): Boolean {
            return getSharedPreferences(context)?.getBoolean(SIGNALING_SERVICE_ENABLED, false) == true
        }

        fun setNotificationTitle(context: Context, value: String?) {
            getSharedPreferences(context)?.edit()?.apply {
                putString(SS_NOTIFICATION_TITLE_KEY, value)
                apply()
            }
        }

        fun setNotificationDescription(context: Context, value: String?) {
            getSharedPreferences(context)?.edit()?.apply {
                putString(SS_NOTIFICATION_DESCRIPTION_KEY, value)
                apply()
            }
        }

        fun getNotificationTitle(context: Context): String {
            val default = context.getString(R.string.signaling_service_notification_name)
            return getSharedPreferences(context)?.getString(SS_NOTIFICATION_TITLE_KEY, default) ?: default
        }

        fun getNotificationDescription(context: Context): String {
            val default = context.getString(R.string.signaling_service_notification_description)
            return getSharedPreferences(context)?.getString(
                SS_NOTIFICATION_DESCRIPTION_KEY, default
            ) ?: default
        }

        fun setOnStartHandler(context: Context, value: Long) {
            getSharedPreferences(context)?.edit()?.apply {
                putLong(ON_START_HANDLER, value)
                apply()
            }
        }

        fun setOnChangedLifecycleHandler(context: Context, value: Long) {
            getSharedPreferences(context)?.edit()?.apply {
                putLong(ON_LIFECYCLE_CHANGE_HANDLER, value)
                apply()
            }
        }

        fun getOnStartHandler(context: Context): Long {
            return getSharedPreferences(context)?.getLong(ON_START_HANDLER, -1)
                ?: throw Exception("OnStartHandler not found")
        }

        fun getOnChangedLifecycleHandler(context: Context): Long {
            return getSharedPreferences(context)?.getLong(ON_LIFECYCLE_CHANGE_HANDLER, -1)
                ?: throw Exception("OnChangedLifecycleHandler not found")
        }

        fun getCallbackDispatcher(context: Context): Long {
            return getSharedPreferences(context)?.getLong(CALLBACK_DISPATCHER, -1)
                ?: throw Exception("CallbackDispatcher not found")
        }

        fun setCallbackDispatcher(context: Context, value: Long) {
            getSharedPreferences(context)?.edit()?.apply {
                putLong(CALLBACK_DISPATCHER, value)
                apply()
            }
        }
    }
}
