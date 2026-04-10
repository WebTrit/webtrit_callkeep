package com.webtrit.callkeep.common

import android.content.Context
import com.webtrit.callkeep.R

/**
 * A delegate for managing SharedPreferences related to incoming and root routes.
 *
 * SharedPreferences are resolved fresh on every call via [Context.applicationContext].
 * This avoids stale references across process restarts or test environments where
 * the Application instance may be recreated between runs.
 */
object StorageDelegate {
    private const val COMMON_PREFERENCES = "COMMON_PREFERENCES"

    private fun sharedPreferences(context: Context) = context.applicationContext.getSharedPreferences(COMMON_PREFERENCES, Context.MODE_PRIVATE)

    object Sound {
        private const val RINGTONE_PATH = "RINGTONE_PATH_KEY"
        private const val RINGBACK_PATH = "RINGBACK_PATH_KEY"

        /** Persists [path] as the ringtone asset path. Passing `null` clears the stored value. */
        fun initRingtonePath(
            context: Context,
            path: String?,
        ) {
            sharedPreferences(context)
                .edit()
                .also { if (path != null) it.putString(RINGTONE_PATH, path) else it.remove(RINGTONE_PATH) }
                .apply()
        }

        fun getRingtonePath(context: Context): String? = sharedPreferences(context).getString(RINGTONE_PATH, null)

        /** Persists [path] as the ringback asset path. Passing `null` clears the stored value. */
        fun initRingbackPath(
            context: Context,
            path: String?,
        ) {
            sharedPreferences(context)
                .edit()
                .also { if (path != null) it.putString(RINGBACK_PATH, path) else it.remove(RINGBACK_PATH) }
                .apply()
        }

        fun getRingbackPath(context: Context): String? = sharedPreferences(context).getString(RINGBACK_PATH, null)
    }

    object IncomingCall {
        private const val FULL_SCREEN = "INCOMING_CALL_FULL_SCREEN"

        /** Persists whether incoming calls should launch in full-screen mode. Defaults to `true`. */
        fun setFullScreen(
            context: Context,
            enabled: Boolean,
        ) {
            sharedPreferences(context).edit().putBoolean(FULL_SCREEN, enabled).apply()
        }

        fun isFullScreen(context: Context): Boolean = sharedPreferences(context).getBoolean(FULL_SCREEN, true)
    }

    object IncomingCallService {
        private const val ON_NOTIFICATION_SYNC = "ON_NOTIFICATION_SYNC"
        private const val INCOMING_CALL_HANDLER = "INCOMING_CALL_HANDLER"
        private const val LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN =
            "LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN"

        fun setLaunchBackgroundIsolateEvenIfAppIsOpen(
            context: Context,
            value: Boolean,
        ) {
            sharedPreferences(context)
                .edit()
                .putBoolean(LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN, value)
                .apply()
        }

        fun isLaunchBackgroundIsolateEvenIfAppIsOpen(context: Context): Boolean = sharedPreferences(context).getBoolean(LAUNCH_BACKGROUND_ISOLATE_EVEN_IF_APP_IS_OPEN, false)

        fun setOnNotificationSync(
            context: Context,
            value: Long,
        ) {
            sharedPreferences(context).edit().putLong(ON_NOTIFICATION_SYNC, value).apply()
        }

        fun getOnNotificationSync(context: Context): Long = sharedPreferences(context).getLong(ON_NOTIFICATION_SYNC, -1)

        fun setCallbackDispatcher(
            context: Context,
            value: Long,
        ) {
            sharedPreferences(context).edit().putLong(INCOMING_CALL_HANDLER, value).apply()
        }

        fun getCallbackDispatcher(context: Context): Long = sharedPreferences(context).getLong(INCOMING_CALL_HANDLER, -1)
    }

    object SignalingService {
        private const val SIGNALING_SERVICE_ENABLED = "SIGNALING_SERVICE_ENABLED"

        private const val SS_NOTIFICATION_TITLE_KEY = "SS_NOTIFICATION_TITLE_KEY"
        private const val SS_NOTIFICATION_DESCRIPTION_KEY = "SS_NOTIFICATION_DESCRIPTION_KEY"

        private const val ON_SYNC_HANDLER = "ON_SYNC_HANDLER"
        private const val CALLBACK_DISPATCHER = "CALLBACK_DISPATCHER"

        fun setSignalingServiceEnabled(
            context: Context,
            value: Boolean,
        ) {
            sharedPreferences(context).edit().putBoolean(SIGNALING_SERVICE_ENABLED, value).apply()
        }

        fun isSignalingServiceEnabled(context: Context): Boolean = sharedPreferences(context).getBoolean(SIGNALING_SERVICE_ENABLED, false)

        fun setNotificationTitle(
            context: Context,
            value: String?,
        ) {
            sharedPreferences(context).edit().putString(SS_NOTIFICATION_TITLE_KEY, value).apply()
        }

        fun setNotificationDescription(
            context: Context,
            value: String?,
        ) {
            sharedPreferences(context)
                .edit()
                .putString(SS_NOTIFICATION_DESCRIPTION_KEY, value)
                .apply()
        }

        fun getNotificationTitle(context: Context): String {
            val default = context.getString(R.string.signaling_service_notification_name)
            return sharedPreferences(context).getString(SS_NOTIFICATION_TITLE_KEY, default)
                ?: default
        }

        fun getNotificationDescription(context: Context): String {
            val default = context.getString(R.string.signaling_service_notification_description)
            return sharedPreferences(context).getString(SS_NOTIFICATION_DESCRIPTION_KEY, default)
                ?: default
        }

        fun setOnSyncHandler(
            context: Context,
            value: Long,
        ) {
            sharedPreferences(context).edit().putLong(ON_SYNC_HANDLER, value).apply()
        }

        fun getOnSyncHandler(context: Context): Long = sharedPreferences(context).getLong(ON_SYNC_HANDLER, -1)

        fun getCallbackDispatcher(context: Context): Long = sharedPreferences(context).getLong(CALLBACK_DISPATCHER, -1)

        fun setCallbackDispatcher(
            context: Context,
            value: Long,
        ) {
            sharedPreferences(context).edit().putLong(CALLBACK_DISPATCHER, value).apply()
        }
    }

    object IncomingCallSmsConfig {
        private const val SMS_PREFIX = "SMS_PREFIX"
        private const val SMS_REGEX_PATTERN = "SMS_REGEX_PATTERN"

        fun setSmsPrefix(
            context: Context,
            prefix: String,
        ) {
            sharedPreferences(context).edit().putString(SMS_PREFIX, prefix).apply()
        }

        fun getSmsPrefix(context: Context): String? = sharedPreferences(context).getString(SMS_PREFIX, null)

        fun setRegexPattern(
            context: Context,
            pattern: String,
        ) {
            sharedPreferences(context).edit().putString(SMS_REGEX_PATTERN, pattern).apply()
        }

        fun getRegexPattern(context: Context): String? = sharedPreferences(context).getString(SMS_REGEX_PATTERN, null)
    }
}
