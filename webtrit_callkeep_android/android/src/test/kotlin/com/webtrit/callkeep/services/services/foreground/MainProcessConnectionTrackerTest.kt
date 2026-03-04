package com.webtrit.callkeep.services.services.foreground

import com.webtrit.callkeep.models.CallMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainProcessConnectionTrackerTest {

    private lateinit var tracker: MainProcessConnectionTracker

    @Before
    fun setUp() {
        tracker = MainProcessConnectionTracker()
    }

    private fun meta(callId: String) = CallMetadata(callId = callId)

    // -------------------------------------------------------------------------
    // add / exists
    // -------------------------------------------------------------------------

    @Test
    fun `exists returns false for unknown callId`() {
        assertFalse(tracker.exists("unknown"))
    }

    @Test
    fun `add then exists returns true`() {
        tracker.add("call-1", meta("call-1"))
        assertTrue(tracker.exists("call-1"))
    }

    @Test
    fun `add overwrites metadata for duplicate callId`() {
        tracker.add("call-1", CallMetadata(callId = "call-1", displayName = "Alice"))
        tracker.add("call-1", CallMetadata(callId = "call-1", displayName = "Bob"))
        assertEquals("Bob", tracker.get("call-1")?.displayName)
    }

    @Test
    fun `multiple distinct callIds tracked independently`() {
        tracker.add("call-1", meta("call-1"))
        tracker.add("call-2", meta("call-2"))
        assertTrue(tracker.exists("call-1"))
        assertTrue(tracker.exists("call-2"))
        assertFalse(tracker.exists("call-3"))
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    fun `remove makes exists return false`() {
        tracker.add("call-1", meta("call-1"))
        tracker.remove("call-1")
        assertFalse(tracker.exists("call-1"))
    }

    @Test
    fun `remove also clears answered state`() {
        tracker.add("call-1", meta("call-1"))
        tracker.markAnswered("call-1")
        tracker.remove("call-1")
        assertFalse(tracker.isAnswered("call-1"))
    }

    @Test
    fun `remove does not affect other callIds`() {
        tracker.add("call-1", meta("call-1"))
        tracker.add("call-2", meta("call-2"))
        tracker.remove("call-1")
        assertTrue(tracker.exists("call-2"))
    }

    @Test
    fun `remove on unknown callId does not throw`() {
        tracker.remove("nonexistent")
    }

    // -------------------------------------------------------------------------
    // get / getAll
    // -------------------------------------------------------------------------

    @Test
    fun `get returns null for unknown callId`() {
        assertNull(tracker.get("unknown"))
    }

    @Test
    fun `get returns the stored metadata`() {
        val m = CallMetadata(callId = "call-1", displayName = "Test")
        tracker.add("call-1", m)
        assertEquals(m, tracker.get("call-1"))
    }

    @Test
    fun `getAll returns all added connections`() {
        tracker.add("call-1", meta("call-1"))
        tracker.add("call-2", meta("call-2"))
        val ids = tracker.getAll().map { it.callId }.toSet()
        assertEquals(setOf("call-1", "call-2"), ids)
    }

    @Test
    fun `getAll returns empty list when tracker is empty`() {
        assertTrue(tracker.getAll().isEmpty())
    }

    @Test
    fun `getAll returns only remaining connections after remove`() {
        tracker.add("call-1", meta("call-1"))
        tracker.add("call-2", meta("call-2"))
        tracker.remove("call-1")
        assertEquals(1, tracker.getAll().size)
        assertEquals("call-2", tracker.getAll().first().callId)
    }

    // -------------------------------------------------------------------------
    // isEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `isEmpty returns true when no connections`() {
        assertTrue(tracker.isEmpty())
    }

    @Test
    fun `isEmpty returns false after add`() {
        tracker.add("call-1", meta("call-1"))
        assertFalse(tracker.isEmpty())
    }

    @Test
    fun `isEmpty returns true after all connections removed`() {
        tracker.add("call-1", meta("call-1"))
        tracker.remove("call-1")
        assertTrue(tracker.isEmpty())
    }

    // -------------------------------------------------------------------------
    // markAnswered / isAnswered
    // -------------------------------------------------------------------------

    @Test
    fun `isAnswered returns false for unknown callId`() {
        assertFalse(tracker.isAnswered("unknown"))
    }

    @Test
    fun `markAnswered then isAnswered returns true`() {
        tracker.markAnswered("call-1")
        assertTrue(tracker.isAnswered("call-1"))
    }

    @Test
    fun `markAnswered does not require prior add`() {
        tracker.markAnswered("call-orphan")
        assertTrue(tracker.isAnswered("call-orphan"))
        assertFalse(tracker.exists("call-orphan"))
    }

    @Test
    fun `markAnswered is idempotent`() {
        tracker.markAnswered("call-1")
        tracker.markAnswered("call-1")
        assertTrue(tracker.isAnswered("call-1"))
    }

    @Test
    fun `isAnswered returns false after remove`() {
        tracker.add("call-1", meta("call-1"))
        tracker.markAnswered("call-1")
        tracker.remove("call-1")
        assertFalse(tracker.isAnswered("call-1"))
    }

    @Test
    fun `isAnswered does not affect other callIds`() {
        tracker.markAnswered("call-1")
        assertFalse(tracker.isAnswered("call-2"))
    }

    @Test
    fun `answered state and exists state are independent`() {
        // A call can be answered without being in the connections map (e.g., race condition)
        tracker.markAnswered("call-orphan")
        assertFalse(tracker.exists("call-orphan"))
        assertTrue(tracker.isAnswered("call-orphan"))

        // A call can exist without being answered
        tracker.add("call-1", meta("call-1"))
        assertTrue(tracker.exists("call-1"))
        assertFalse(tracker.isAnswered("call-1"))
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    fun `clear removes all connections`() {
        tracker.add("call-1", meta("call-1"))
        tracker.add("call-2", meta("call-2"))
        tracker.clear()
        assertTrue(tracker.isEmpty())
        assertNull(tracker.get("call-1"))
        assertNull(tracker.get("call-2"))
    }

    @Test
    fun `clear removes all answered state`() {
        tracker.markAnswered("call-1")
        tracker.markAnswered("call-2")
        tracker.clear()
        assertFalse(tracker.isAnswered("call-1"))
        assertFalse(tracker.isAnswered("call-2"))
    }

    @Test
    fun `clear on empty tracker does not throw`() {
        tracker.clear()
        assertTrue(tracker.isEmpty())
    }

    @Test
    fun `tracker is usable after clear`() {
        tracker.add("call-1", meta("call-1"))
        tracker.clear()
        tracker.add("call-2", meta("call-2"))
        assertTrue(tracker.exists("call-2"))
        assertFalse(tracker.exists("call-1"))
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    fun `toString includes tracked callIds`() {
        tracker.add("call-abc", meta("call-abc"))
        tracker.markAnswered("call-abc")
        val str = tracker.toString()
        assertNotNull(str)
        assertTrue(str.contains("call-abc"))
    }
}
