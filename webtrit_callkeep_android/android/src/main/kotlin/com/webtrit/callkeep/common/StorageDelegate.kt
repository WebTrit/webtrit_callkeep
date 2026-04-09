package com.webtrit.callkeep.common

import android.content.Context

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

    object Timeout {
        private const val INCOMING_CALL_TIMEOUT_MS = "INCOMING_CALL_TIMEOUT_MS"
        private const val OUTGOING_CALL_TIMEOUT_MS = "OUTGOING_CALL_TIMEOUT_MS"
        private const val DEFAULT_TIMEOUT_MS = 60_000L

        fun setIncomingCallTimeoutMs(
            context: Context,
            ms: Long,
        ) {
            sharedPreferences(context).edit().putLong(INCOMING_CALL_TIMEOUT_MS, ms).apply()
        }

        fun getIncomingCallTimeoutMs(context: Context): Long = sharedPreferences(context).getLong(INCOMING_CALL_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)

        fun setOutgoingCallTimeoutMs(
            context: Context,
            ms: Long,
        ) {
            sharedPreferences(context).edit().putLong(OUTGOING_CALL_TIMEOUT_MS, ms).apply()
        }

        fun getOutgoingCallTimeoutMs(context: Context): Long = sharedPreferences(context).getLong(OUTGOING_CALL_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
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
