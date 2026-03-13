package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The root directory used by the Flutter tool to bundle assets inside the APK.
 *
 * Defined by the Flutter build system and stable across Flutter versions.
 * See: https://docs.flutter.dev/ui/assets/assets-and-images#loading-assets
 *
 * All asset names passed to [android.content.res.AssetManager] must be
 * prefixed with this path (e.g. "flutter_assets/ringtone.mp3").
 */
private const val FLUTTER_ASSETS_DIR = "flutter_assets"

/**
 * Caches APK assets to disk and returns file URIs for use by the Android media stack.
 *
 * Asset names are resolved using [FLUTTER_ASSETS_DIR], which is the standard
 * Flutter asset directory bundled into every Flutter APK. This class has no
 * dependency on the Flutter plugin API and works identically in any OS process —
 * including isolated processes such as `:callkeep_core` that have no FlutterEngine.
 */
class AssetCacheManager(private val context: Context) {
    private val cacheDir: File by lazy { context.cacheDir }

    fun getAsset(asset: String): Uri? {
        val assetPath = "$FLUTTER_ASSETS_DIR/$asset"
        val fileName = assetPath.toUri().lastPathSegment ?: "cache"

        // Note: cached files are keyed by file name only — two different assets
        // with the same file name would collide.
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
