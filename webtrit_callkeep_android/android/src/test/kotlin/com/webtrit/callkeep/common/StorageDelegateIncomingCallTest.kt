package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
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
class StorageDelegateIncomingCallTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // -------------------------------------------------------------------------
    // isFullScreen — default / set / get / toggle
    // -------------------------------------------------------------------------

    @Test
    fun `isFullScreen returns true by default when key is absent`() {
        assertTrue(StorageDelegate.IncomingCall.isFullScreen(context))
    }

    @Test
    fun `setFullScreen false is returned by isFullScreen`() {
        StorageDelegate.IncomingCall.setFullScreen(context, false)
        assertFalse(StorageDelegate.IncomingCall.isFullScreen(context))
    }

    @Test
    fun `setFullScreen true is returned by isFullScreen`() {
        StorageDelegate.IncomingCall.setFullScreen(context, true)
        assertTrue(StorageDelegate.IncomingCall.isFullScreen(context))
    }

    @Test
    fun `setFullScreen can be toggled from false to true`() {
        StorageDelegate.IncomingCall.setFullScreen(context, false)
        assertFalse(StorageDelegate.IncomingCall.isFullScreen(context))

        StorageDelegate.IncomingCall.setFullScreen(context, true)
        assertTrue(StorageDelegate.IncomingCall.isFullScreen(context))
    }

    @Test
    fun `setFullScreen can be toggled from true to false`() {
        StorageDelegate.IncomingCall.setFullScreen(context, true)
        assertTrue(StorageDelegate.IncomingCall.isFullScreen(context))

        StorageDelegate.IncomingCall.setFullScreen(context, false)
        assertFalse(StorageDelegate.IncomingCall.isFullScreen(context))
    }
}
