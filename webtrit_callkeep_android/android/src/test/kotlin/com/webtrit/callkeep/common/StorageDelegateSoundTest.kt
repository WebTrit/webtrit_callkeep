package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class StorageDelegateSoundTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // -------------------------------------------------------------------------
    // initRingtonePath — null clears the stored value
    // -------------------------------------------------------------------------

    @Test
    fun `initRingtonePath stores the path`() {
        StorageDelegate.Sound.initRingtonePath(context, "/assets/ringtone.mp3")
        assertEquals("/assets/ringtone.mp3", StorageDelegate.Sound.getRingtonePath(context))
    }

    @Test
    fun `initRingtonePath with null clears the stored path`() {
        StorageDelegate.Sound.initRingtonePath(context, "/assets/ringtone.mp3")
        StorageDelegate.Sound.initRingtonePath(context, null)
        assertNull(StorageDelegate.Sound.getRingtonePath(context))
    }

    // -------------------------------------------------------------------------
    // initRingbackPath — null clears the stored value
    // -------------------------------------------------------------------------

    @Test
    fun `initRingbackPath stores the path`() {
        StorageDelegate.Sound.initRingbackPath(context, "/assets/ringback.mp3")
        assertEquals("/assets/ringback.mp3", StorageDelegate.Sound.getRingbackPath(context))
    }

    @Test
    fun `initRingbackPath with null clears the stored path`() {
        StorageDelegate.Sound.initRingbackPath(context, "/assets/ringback.mp3")
        StorageDelegate.Sound.initRingbackPath(context, null)
        assertNull(StorageDelegate.Sound.getRingbackPath(context))
    }
}
