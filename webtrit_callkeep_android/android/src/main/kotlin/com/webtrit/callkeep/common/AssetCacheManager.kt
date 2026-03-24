package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.FileOutputStream

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
 * Process-wide singleton that caches APK assets to disk and returns file URIs
 * for use by the Android media stack.
 *
 * Call [init] once with any [Context] before the first [getAsset] call.
 * Initialization is identical in all OS processes — no Flutter API is required.
 */
@SuppressLint("StaticFieldLeak")
object AssetCacheManager {
    private var context: Context? = null

    /** Initializes the singleton with [context]. Safe to call from any OS process. No-op if already initialized. */
    @Synchronized
    fun init(context: Context) {
        if (this.context == null) {
            this.context = context.applicationContext
        } else {
            Log.i("AssetCacheManager", "AssetCacheManager is already initialized.")
        }
    }

    /** Resets singleton state. For use in tests only. */
    @VisibleForTesting
    internal fun reset() {
        context = null
    }

    /**
     * Returns a `file://` [Uri] for [asset], caching it to disk on first access.
     *
     * @param asset Logical asset name relative to the Flutter assets directory (e.g. `"ringtone.mp3"`).
     * @throws IllegalStateException if [init] has not been called.
     * @throws java.io.IOException if the asset does not exist in the APK.
     */
    fun getAsset(asset: String): Uri {
        val ctx =
            context
                ?: throw IllegalStateException("AssetCacheManager is not initialized. Call init() first.")

        val assetPath = "$FLUTTER_ASSETS_DIR/$asset"
        val fileName = Uri.parse(assetPath).lastPathSegment ?: "cache"

        // Note: cached files are keyed by file name only — two different assets
        // with the same file name would collide.
        val cachedFile = File(ctx.cacheDir, fileName)
        if (cachedFile.exists()) {
            return Uri.fromFile(cachedFile)
        }

        return cacheAsset(ctx, assetPath, fileName).let { Uri.fromFile(File(it)) }
    }

    /**
     * Copies an asset from the APK to [Context.getCacheDir] and returns the absolute path.
     *
     * This step is required because the Android media stack ([android.media.RingtoneManager],
     * [android.media.MediaPlayer]) expects a `file://` URI pointing to a real file on disk.
     * APK assets are only accessible as an [java.io.InputStream] via [android.content.res.AssetManager]
     * and cannot be referenced by a file URI directly.
     */
    private fun cacheAsset(
        context: Context,
        assetPath: String,
        fileName: String,
    ): String {
        val cachedFile = File(context.cacheDir, fileName)
        val inputStream = context.assets.open(assetPath)
        inputStream.use { stream ->
            FileOutputStream(cachedFile).use { outputStream ->
                stream.copyTo(outputStream, bufferSize = 1024)
            }
        }
        return cachedFile.absolutePath
    }
}
