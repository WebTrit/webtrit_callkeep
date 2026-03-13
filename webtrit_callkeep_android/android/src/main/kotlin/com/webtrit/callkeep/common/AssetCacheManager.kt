package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Caches Android assets to disk and returns file URIs for use by the media stack.
 *
 * Asset name → path resolution is fully delegated to [assetPathResolver], so this
 * class has no knowledge of any SDK conventions and works in any OS process.
 *
 * @param assetPathResolver maps a logical asset name (e.g. "ringtone.mp3") to the
 *   path understood by [android.content.res.AssetManager] (e.g. "flutter_assets/ringtone.mp3").
 */
class AssetCacheManager(
    private val context: Context,
    private val assetPathResolver: (String) -> String,
) {
    private val cacheDir: File by lazy { context.cacheDir }

    fun getAsset(asset: String): Uri? {
        val assetPath = assetPathResolver(asset)
        val fileName = assetPath.toUri().lastPathSegment ?: "cache"

        // For note: there may be issues with cached data if, for example,
        // another sound is saved under the same name.
        val cachedFile = File(cacheDir, fileName)
        if (cachedFile.exists()) {
            return Uri.fromFile(cachedFile)
        }

        return cacheAsset(assetPath, fileName).let { Uri.fromFile(File(it)) }
    }

    private fun cacheAsset(assetPath: String, fileName: String): String {
        val cachedFile = File(context.cacheDir, fileName)
        try {
            val inputStream = context.assets.open(assetPath)
            inputStream.use { stream ->
                FileOutputStream(cachedFile).use { outputStream ->
                    stream.copyTo(outputStream, bufferSize = 1024)
                }
            }
            return cachedFile.absolutePath
        } catch (e: IOException) {
            throw e
        }
    }
}
