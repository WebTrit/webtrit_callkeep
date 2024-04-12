package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri

import io.flutter.embedding.engine.plugins.FlutterPlugin

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages caching operations for assets.
 * Due to limitations in the Flutter API, we use Android's AssetManager to obtain the lookup key for assets,
 * instead of directly using loader.getLookupKeyForAsset().
 *
 * Documentation: https://api.flutter.dev/javadoc/io/flutter/view/FlutterMain.html#getLookupKeyForAsset(java.lang.String)
 */
class FlutterAssetManager(private val context: Context, private var assets: FlutterPlugin.FlutterAssets) {
    private val cacheDir: File by lazy { context.cacheDir }

    fun getAsset(asset: String): Uri? {
        val assets = assets.getAssetFilePathByName(asset)
        val fileName = Uri.parse(assets).lastPathSegment ?: "cache"

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
