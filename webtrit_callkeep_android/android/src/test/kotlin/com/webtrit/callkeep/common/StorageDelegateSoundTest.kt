package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    // incomingCallFullScreen — set / get
    // -------------------------------------------------------------------------

    @Test
    fun `setIncomingCallFullScreen false is returned by isIncomingCallFullScreen`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)
        assertFalse(StorageDelegate.Sound.isIncomingCallFullScreen(context))
    }

    @Test
    fun `setIncomingCallFullScreen true is returned by isIncomingCallFullScreen`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, true)
        assertTrue(StorageDelegate.Sound.isIncomingCallFullScreen(context))
    }

    @Test
    fun `setIncomingCallFullScreen can be toggled from false to true`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)
        assertFalse(StorageDelegate.Sound.isIncomingCallFullScreen(context))

        StorageDelegate.Sound.setIncomingCallFullScreen(context, true)
        assertTrue(StorageDelegate.Sound.isIncomingCallFullScreen(context))
    }

    @Test
    fun `setIncomingCallFullScreen can be toggled from true to false`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, true)
        assertTrue(StorageDelegate.Sound.isIncomingCallFullScreen(context))

        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)
        assertFalse(StorageDelegate.Sound.isIncomingCallFullScreen(context))
    }

    @Test
    fun `isIncomingCallFullScreen is independent from ringtone path`() {
        StorageDelegate.Sound.initRingtonePath(context, "/path/to/ringtone.mp3")
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)

        assertFalse(StorageDelegate.Sound.isIncomingCallFullScreen(context))
        assertEquals("/path/to/ringtone.mp3", StorageDelegate.Sound.getRingtonePath(context))
    }

    @Test
    fun `isIncomingCallFullScreen is independent from ringback path`() {
        StorageDelegate.Sound.initRingbackPath(context, "/path/to/ringback.mp3")
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)

        assertFalse(StorageDelegate.Sound.isIncomingCallFullScreen(context))
        assertEquals("/path/to/ringback.mp3", StorageDelegate.Sound.getRingbackPath(context))
    }
}
