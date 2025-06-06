package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri

import io.flutter.embedding.engine.plugins.FlutterPlugin

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.net.toUri

/**
 * Manages caching operations for assets.
 * Due to limitations in the Flutter API, this class uses Android's AssetManager to obtain the lookup key for assets,
 * instead of directly using Flutter's getAssetFilePathByName() method.
 * The returned file path is relative to the Android app's standard assets directory. Therefore, the path is appropriate to pass to Android's AssetManager, but it should not be used as an absolute path.
 *
 * For more information, refer to the Flutter API documentation:
 * {@link https://api.flutter.dev/javadoc/io/flutter/embedding/engine/plugins/FlutterPlugin.FlutterAssets.html#getAssetFilePathByName(java.lang.String)}
 */
class FlutterAssetManager(private val context: Context, private var assets: FlutterPlugin.FlutterAssets) {
    private val cacheDir: File by lazy { context.cacheDir }

    fun getAsset(asset: String): Uri? {
        val assets = assets.getAssetFilePathByName(asset)
        val fileName = assets.toUri().lastPathSegment ?: "cache"

        // For note: there may be issues with cached data if, for example, another sound is saved under the same name.
        val cachedFile = File(cacheDir, fileName)
        if (cachedFile.exists()) {
            return Uri.fromFile(cachedFile)
        }

        return cacheAsset(assets, fileName).let { Uri.fromFile(File(it)) }
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
