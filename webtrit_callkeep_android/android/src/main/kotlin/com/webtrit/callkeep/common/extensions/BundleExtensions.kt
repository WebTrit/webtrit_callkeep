package com.webtrit.callkeep.common.extensions

import android.os.Bundle

/**
 * Safely retrieves a Long value from the Bundle.
 * Returns null if the key is not present, avoiding the default 0L return value.
 */
fun Bundle.getLongOrNull(key: String): Long? {
    return if (containsKey(key)) getLong(key) else null
}

/**
 * Safely retrieves a Char value from the Bundle.
 * Returns null if the key is not present, avoiding the default '\u0000' return value.
 */
fun Bundle.getCharOrNull(key: String): Char? {
    return if (containsKey(key)) getChar(key) else null
}

/**
 * Safely retrieves a Boolean value from the Bundle.
 * Returns null if the key is not present, avoiding the default false value.
 */
fun Bundle.getBooleanOrNull(key: String): Boolean? {
    return if (containsKey(key)) getBoolean(key) else null
}

/**
 * Safely retrieves a String value from the Bundle.
 * Returns null if the key is not present, avoiding ambiguity with default values.
 */
fun Bundle.getStringOrNull(key: String): String? {
    return if (containsKey(key)) getString(key) else null
}
