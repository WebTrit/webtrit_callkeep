package com.webtrit.callkeep.common

import android.content.Context
import android.net.Uri
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AssetCacheManagerTest {
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        AssetCacheManager.reset()
    }

    @After
    fun tearDown() {
        AssetCacheManager.reset()
    }

    @Test
    fun `getAsset throws before init`() {
        var thrown = false
        try {
            AssetCacheManager.getAsset("ringtone.mp3")
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `init is idempotent -- second call does not throw`() {
        AssetCacheManager.init(ctx)
        AssetCacheManager.init(ctx) // must not throw or reset state
        // Seed cache so getAsset does not attempt APK read
        File(ctx.cacheDir, "ringtone.mp3").writeBytes(ByteArray(4))
        val uri = AssetCacheManager.getAsset("ringtone.mp3")
        assertTrue(uri.toString().isNotEmpty())
    }

    @Test
    fun `getAsset returns file URI for cached asset`() {
        AssetCacheManager.init(ctx)

        // Pre-populate cache to simulate a previously cached asset
        val cachedFile = File(ctx.cacheDir, "ringtone.mp3")
        cachedFile.writeBytes(ByteArray(8))

        val uri = AssetCacheManager.getAsset("ringtone.mp3")

        assertEquals(Uri.fromFile(cachedFile), uri)
    }

    @Test
    fun `getAsset cache hit does not read from APK assets`() {
        AssetCacheManager.init(ctx)

        // If the cached file exists, the APK asset path must never be opened.
        // We verify this indirectly: an asset that does not exist in the APK
        // (Robolectric has no flutter_assets/) still returns a URI when cached.
        val cachedFile = File(ctx.cacheDir, "nonexistent_in_apk.mp3")
        cachedFile.writeBytes(ByteArray(4))

        val uri = AssetCacheManager.getAsset("nonexistent_in_apk.mp3")

        assertEquals(Uri.fromFile(cachedFile), uri)
    }

    @Test
    fun `getAsset resolves path with flutter_assets prefix`() {
        AssetCacheManager.init(ctx)

        // Seed the cache using the expected file name (last segment of the resolved path).
        // flutter_assets/sub/sound.mp3 -> last segment = sound.mp3
        val cachedFile = File(ctx.cacheDir, "sound.mp3")
        cachedFile.writeBytes(ByteArray(4))

        val uri = AssetCacheManager.getAsset("sub/sound.mp3")

        assertEquals(Uri.fromFile(cachedFile), uri)
    }
}
