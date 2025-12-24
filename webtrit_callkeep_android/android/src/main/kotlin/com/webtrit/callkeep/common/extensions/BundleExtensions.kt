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
