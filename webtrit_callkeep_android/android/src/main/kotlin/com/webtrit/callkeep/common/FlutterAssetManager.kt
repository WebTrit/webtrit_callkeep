package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages caching operations for Flutter assets.
 *
 * Resolves asset names using the standard Flutter asset path convention
 * ("flutter_assets/<name>") and caches them to disk for use by the Android
 * media stack. This class has no dependency on the Flutter plugin API and can
 * be used from any OS process, including isolated processes without a live
 * FlutterEngine.
 */
class FlutterAssetManager(private val context: Context) {
    private val cacheDir: File by lazy { context.cacheDir }

    fun getAsset(asset: String): Uri? {
        val assetPath = "flutter_assets/$asset"
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
