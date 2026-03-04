package com.webtrit.callkeep.services.services.foreground

import android.os.Build
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.OutgoingFailureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class FailedCallsStoreTest {

    private lateinit var store: FailedCallsStore

    @Before
    fun setUp() {
        store = FailedCallsStore()
    }

    private fun meta(callId: String) = CallMetadata(callId = callId)

    @Test
    fun `getAll returns empty list initially`() {
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `add then getAll returns the entry`() {
        store.add(meta("call-1"), OutgoingFailureSource.TIMEOUT, "timed out")
        val all = store.getAll()
        assertEquals(1, all.size)
        assertEquals("call-1", all.first().callId)
        assertEquals(OutgoingFailureSource.TIMEOUT, all.first().source)
        assertEquals("timed out", all.first().reason)
    }

    @Test
    fun `add with null reason stores null`() {
        store.add(meta("call-1"), OutgoingFailureSource.CS_CALLBACK, null)
        val entry = store.getAll().first()
        assertEquals(null, entry.reason)
    }

    @Test
    fun `add with same callId overwrites previous entry`() {
        store.add(meta("call-1"), OutgoingFailureSource.TIMEOUT, "first")
        store.add(meta("call-1"), OutgoingFailureSource.CS_CALLBACK, "second")
        val all = store.getAll()
        assertEquals(1, all.size)
        assertEquals(OutgoingFailureSource.CS_CALLBACK, all.first().source)
        assertEquals("second", all.first().reason)
    }

    @Test
    fun `add multiple distinct callIds stores all`() {
        store.add(meta("call-1"), OutgoingFailureSource.TIMEOUT, "t1")
        store.add(meta("call-2"), OutgoingFailureSource.TIMEOUT, "t2")
        store.add(meta("call-3"), OutgoingFailureSource.CS_CALLBACK, "c3")
        assertEquals(3, store.getAll().size)
    }

    @Test
    fun `getAll returns entries sorted by timestamp descending`() {
        store.add(meta("call-1"), OutgoingFailureSource.TIMEOUT, null)
        Thread.sleep(5)
        store.add(meta("call-2"), OutgoingFailureSource.TIMEOUT, null)
        Thread.sleep(5)
        store.add(meta("call-3"), OutgoingFailureSource.TIMEOUT, null)

        val all = store.getAll()
        assertEquals("call-3", all[0].callId)
        assertEquals("call-2", all[1].callId)
        assertEquals("call-1", all[2].callId)
    }

    @Test
    fun `FailedCallInfo timestamp is set at add time`() {
        val before = System.currentTimeMillis()
        store.add(meta("call-1"), OutgoingFailureSource.TIMEOUT, null)
        val after = System.currentTimeMillis()

        val ts = store.getAll().first().timestamp
        assertTrue(ts in before..after)
    }

    @Test
    fun `metadata is preserved in the stored entry`() {
        val m = CallMetadata(callId = "call-1", displayName = "Alice")
        store.add(m, OutgoingFailureSource.CS_CALLBACK, "reason")
        assertEquals(m, store.getAll().first().metadata)
    }
}
