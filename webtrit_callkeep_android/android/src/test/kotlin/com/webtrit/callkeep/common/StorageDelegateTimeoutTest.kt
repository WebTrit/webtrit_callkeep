package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class StorageDelegateTimeoutTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // -------------------------------------------------------------------------
    // Incoming timeout — default / set / get
    // -------------------------------------------------------------------------

    @Test
    fun `getIncomingCallTimeoutMs returns 60000 by default when key is absent`() {
        assertEquals(60_000L, StorageDelegate.Timeout.getIncomingCallTimeoutMs(context))
    }

    @Test
    fun `setIncomingCallTimeoutMs is returned by getIncomingCallTimeoutMs`() {
        StorageDelegate.Timeout.setIncomingCallTimeoutMs(context, 120_000L)
        assertEquals(120_000L, StorageDelegate.Timeout.getIncomingCallTimeoutMs(context))
    }

    @Test
    fun `setIncomingCallTimeoutMs can be updated to a new value`() {
        StorageDelegate.Timeout.setIncomingCallTimeoutMs(context, 90_000L)
        assertEquals(90_000L, StorageDelegate.Timeout.getIncomingCallTimeoutMs(context))

        StorageDelegate.Timeout.setIncomingCallTimeoutMs(context, 30_000L)
        assertEquals(30_000L, StorageDelegate.Timeout.getIncomingCallTimeoutMs(context))
    }

    // -------------------------------------------------------------------------
    // Outgoing timeout — default / set / get
    // -------------------------------------------------------------------------

    @Test
    fun `getOutgoingCallTimeoutMs returns 60000 by default when key is absent`() {
        assertEquals(60_000L, StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context))
    }

    @Test
    fun `setOutgoingCallTimeoutMs is returned by getOutgoingCallTimeoutMs`() {
        StorageDelegate.Timeout.setOutgoingCallTimeoutMs(context, 120_000L)
        assertEquals(120_000L, StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context))
    }

    @Test
    fun `setOutgoingCallTimeoutMs can be updated to a new value`() {
        StorageDelegate.Timeout.setOutgoingCallTimeoutMs(context, 90_000L)
        assertEquals(90_000L, StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context))

        StorageDelegate.Timeout.setOutgoingCallTimeoutMs(context, 30_000L)
        assertEquals(30_000L, StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context))
    }

    // -------------------------------------------------------------------------
    // Incoming and outgoing are independent
    // -------------------------------------------------------------------------

    @Test
    fun `incoming and outgoing timeouts are stored independently`() {
        StorageDelegate.Timeout.setIncomingCallTimeoutMs(context, 120_000L)
        StorageDelegate.Timeout.setOutgoingCallTimeoutMs(context, 45_000L)

        assertEquals(120_000L, StorageDelegate.Timeout.getIncomingCallTimeoutMs(context))
        assertEquals(45_000L, StorageDelegate.Timeout.getOutgoingCallTimeoutMs(context))
    }
}
