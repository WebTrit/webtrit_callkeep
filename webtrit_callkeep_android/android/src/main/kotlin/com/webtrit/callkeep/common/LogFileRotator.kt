package com.webtrit.callkeep.common

import java.io.File

object LogFileRotator {
    private const val MAX_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB

    /**
     * Rotates [file] if it exceeds [MAX_SIZE_BYTES].
     * Renames it to <path>.1 (deleting any existing .1 first).
     * Must be called inside a @Synchronized block (caller's responsibility).
     */
    fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_SIZE_BYTES) return
        val rotated = File("${file.path}.1")
        if (rotated.exists()) rotated.delete()
        file.renameTo(rotated)
    }
}
