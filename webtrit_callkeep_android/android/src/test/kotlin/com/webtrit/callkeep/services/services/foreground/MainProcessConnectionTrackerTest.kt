package com.webtrit.callkeep.services.services.foreground

import android.os.Build
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.models.CallMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MainProcessConnectionTracker].
 *
 * Covers the full call lifecycle: addPending -> promote -> markAnswered -> markTerminated,
 * deferred-answer reservation, tearDown drain, and clear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MainProcessConnectionTrackerTest {

    private lateinit var tracker: MainProcessConnectionTracker

    private fun metadata(callId: String = "call-1") = CallMetadata(callId = callId)

    @Before
    fun setUp() {
        tracker = MainProcessConnectionTracker()
    }

    // -------------------------------------------------------------------------
    // addPending
    // -------------------------------------------------------------------------

    @Test
    fun `addPending — isPending returns true`() {
        tracker.addPending("call-1")
        assertTrue(tracker.isPending("call-1"))
    }

    @Test
    fun `addPending — exists returns false before promote`() {
        // Pending calls are not in connections; only promote() populates connections.
        // This ensures answerCall routes to the deferred-answer path, not startAnswerCall.
        tracker.addPending("call-1")
        assertFalse(tracker.exists("call-1"))
    }

    @Test
    fun `addPending — isTerminated returns false`() {
        tracker.addPending("call-1")
        assertFalse(tracker.isTerminated("call-1"))
    }

    @Test
    fun `addPending — get returns null before promote`() {
        tracker.addPending("call-1")
        assertNull(tracker.get("call-1"))
    }

    @Test
    fun `addPending — getAll does not include pending calls`() {
        tracker.addPending("call-1")
        assertTrue(tracker.getAll().isEmpty())
    }

    // -------------------------------------------------------------------------
    // promote
    // -------------------------------------------------------------------------

    @Test
    fun `promote — isPending becomes false`() {
        tracker.addPending("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        assertFalse(tracker.isPending("call-1"))
    }

    @Test
    fun `promote — exists returns true`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        assertTrue(tracker.exists("call-1"))
    }

    @Test
    fun `promote — getState returns supplied state`() {
        tracker.addPending("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        assertEquals(PCallkeepConnectionState.STATE_RINGING, tracker.getState("call-1"))
    }

    @Test
    fun `promote outgoing — getState returns STATE_DIALING`() {
        tracker.addPending("call-out")
        tracker.promote("call-out", metadata("call-out"), PCallkeepConnectionState.STATE_DIALING)
        assertEquals(PCallkeepConnectionState.STATE_DIALING, tracker.getState("call-out"))
    }

    @Test
    fun `promote without prior addPending — still registers the call`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        assertTrue(tracker.exists("call-1"))
        assertFalse(tracker.isPending("call-1"))
    }

    // -------------------------------------------------------------------------
    // markAnswered
    // -------------------------------------------------------------------------

    @Test
    fun `markAnswered — isAnswered returns true`() {
        tracker.addPending("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        assertTrue(tracker.isAnswered("call-1"))
    }

    @Test
    fun `markAnswered — getState advances to STATE_ACTIVE`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        assertEquals(PCallkeepConnectionState.STATE_ACTIVE, tracker.getState("call-1"))
    }

    @Test
    fun `markAnswered — exists remains true`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        assertTrue(tracker.exists("call-1"))
    }

    // -------------------------------------------------------------------------
    // markTerminated
    // -------------------------------------------------------------------------

    @Test
    fun `markTerminated — isTerminated returns true`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertTrue(tracker.isTerminated("call-1"))
    }

    @Test
    fun `markTerminated — exists returns false`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertFalse(tracker.exists("call-1"))
    }

    @Test
    fun `markTerminated — isPending becomes false`() {
        tracker.addPending("call-1")
        tracker.markTerminated("call-1")
        assertFalse(tracker.isPending("call-1"))
    }

    @Test
    fun `markTerminated — isAnswered becomes false`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        tracker.markTerminated("call-1")
        assertFalse(tracker.isAnswered("call-1"))
    }

    @Test
    fun `markTerminated — getAll excludes the call`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.promote("call-2", metadata("call-2"), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        val allIds = tracker.getAll().map { it.callId }
        assertFalse(allIds.contains("call-1"))
        assertTrue(allIds.contains("call-2"))
    }

    @Test
    fun `markTerminated — getState returns STATE_DISCONNECTED`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertEquals(PCallkeepConnectionState.STATE_DISCONNECTED, tracker.getState("call-1"))
    }

    @Test
    fun `markTerminated — clears pending answer reservation`() {
        tracker.addPending("call-1")
        tracker.reserveAnswer("call-1")
        tracker.markTerminated("call-1")
        // consumeAnswer must return false: reservation was cleared by markTerminated
        assertFalse(tracker.consumeAnswer("call-1"))
    }

    // -------------------------------------------------------------------------
    // markHeld
    // -------------------------------------------------------------------------

    @Test
    fun `markHeld true — getState returns STATE_HOLDING`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        tracker.markHeld("call-1", true)
        assertEquals(PCallkeepConnectionState.STATE_HOLDING, tracker.getState("call-1"))
    }

    @Test
    fun `markHeld false — getState returns STATE_ACTIVE`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        tracker.markHeld("call-1", true)
        tracker.markHeld("call-1", false)
        assertEquals(PCallkeepConnectionState.STATE_ACTIVE, tracker.getState("call-1"))
    }

    // -------------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------------

    @Test
    fun `getAll — empty on fresh tracker`() {
        assertTrue(tracker.getAll().isEmpty())
    }

    @Test
    fun `getAll — returns all promoted calls`() {
        tracker.promote("call-1", metadata("call-1"), PCallkeepConnectionState.STATE_RINGING)
        tracker.promote("call-2", metadata("call-2"), PCallkeepConnectionState.STATE_DIALING)
        assertEquals(2, tracker.getAll().size)
    }

    // -------------------------------------------------------------------------
    // toPCallkeepConnection
    // -------------------------------------------------------------------------

    @Test
    fun `toPCallkeepConnection — returns null for unknown callId`() {
        assertNull(tracker.toPCallkeepConnection("unknown"))
    }

    @Test
    fun `toPCallkeepConnection — returns connection with correct callId and state`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        val connection = tracker.toPCallkeepConnection("call-1")
        assertNotNull(connection)
        assertEquals("call-1", connection!!.callId)
        assertEquals(PCallkeepConnectionState.STATE_RINGING, connection.state)
    }

    @Test
    fun `toPCallkeepConnection — returns null after markTerminated`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertNull(tracker.toPCallkeepConnection("call-1"))
    }

    // -------------------------------------------------------------------------
    // removePending
    // -------------------------------------------------------------------------

    @Test
    fun `removePending — isPending becomes false`() {
        tracker.addPending("call-1")
        tracker.removePending("call-1")
        assertFalse(tracker.isPending("call-1"))
    }

    @Test
    fun `removePending — drainUnconnectedPendingCallIds excludes removed call`() {
        tracker.addPending("call-1")
        tracker.addPending("call-2")
        tracker.removePending("call-1")
        val drained = tracker.drainUnconnectedPendingCallIds()
        assertFalse(drained.contains("call-1"))
        assertTrue(drained.contains("call-2"))
    }

    @Test
    fun `removePending — no-op when callId was never pending`() {
        // Should not throw or affect other state.
        tracker.removePending("unknown")
        assertFalse(tracker.isPending("unknown"))
    }

    // -------------------------------------------------------------------------
    // reserveAnswer / consumeAnswer
    // -------------------------------------------------------------------------

    @Test
    fun `reserveAnswer then consumeAnswer returns true and clears`() {
        tracker.reserveAnswer("call-1")
        assertTrue(tracker.consumeAnswer("call-1"))
        assertFalse(tracker.consumeAnswer("call-1"))
    }

    @Test
    fun `consumeAnswer without reserveAnswer returns false`() {
        assertFalse(tracker.consumeAnswer("call-1"))
    }

    // -------------------------------------------------------------------------
    // drainUnconnectedPendingCallIds
    // -------------------------------------------------------------------------

    @Test
    fun `drainUnconnectedPendingCallIds — returns all pending callIds`() {
        tracker.addPending("call-1")
        tracker.addPending("call-2")
        val drained = tracker.drainUnconnectedPendingCallIds()
        assertEquals(setOf("call-1", "call-2"), drained)
    }

    @Test
    fun `drainUnconnectedPendingCallIds — promoted calls are not drained`() {
        tracker.addPending("call-pending")
        tracker.addPending("call-promoted")
        tracker.promote("call-promoted", metadata("call-promoted"), PCallkeepConnectionState.STATE_RINGING)
        val drained = tracker.drainUnconnectedPendingCallIds()
        assertFalse(drained.contains("call-promoted"))
        assertTrue(drained.contains("call-pending"))
    }

    @Test
    fun `drainUnconnectedPendingCallIds — subsequent isPending returns false`() {
        tracker.addPending("call-1")
        tracker.drainUnconnectedPendingCallIds()
        assertFalse(tracker.isPending("call-1"))
    }

    @Test
    fun `drainUnconnectedPendingCallIds — drained call never appeared in getAll`() {
        // Pending calls are not in connections, so getAll was already empty before drain.
        tracker.addPending("call-1")
        assertTrue(tracker.getAll().isEmpty())
        tracker.drainUnconnectedPendingCallIds()
        assertTrue(tracker.getAll().isEmpty())
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    fun `clear — resets all state`() {
        tracker.addPending("call-1")
        tracker.promote("call-2", metadata("call-2"), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-2")
        tracker.markTerminated("call-1")
        tracker.reserveAnswer("call-3")

        tracker.clear()

        assertTrue(tracker.getAll().isEmpty())
        assertFalse(tracker.isPending("call-1"))
        assertFalse(tracker.exists("call-2"))
        assertFalse(tracker.isTerminated("call-1"))
        assertFalse(tracker.consumeAnswer("call-3"))
    }

    // -------------------------------------------------------------------------
    // multiple calls lifecycle
    // -------------------------------------------------------------------------

    @Test
    fun `full lifecycle — incoming call from pending to terminated`() {
        val id = "call-lifecycle"
        val meta = metadata(id)

        tracker.addPending(id)
        assertTrue(tracker.isPending(id))
        assertFalse(tracker.exists(id))  // not yet promoted, so not in connections

        tracker.promote(id, meta, PCallkeepConnectionState.STATE_RINGING)
        assertFalse(tracker.isPending(id))
        assertEquals(PCallkeepConnectionState.STATE_RINGING, tracker.getState(id))

        tracker.markAnswered(id)
        assertTrue(tracker.isAnswered(id))
        assertEquals(PCallkeepConnectionState.STATE_ACTIVE, tracker.getState(id))

        tracker.markTerminated(id)
        assertFalse(tracker.exists(id))
        assertTrue(tracker.isTerminated(id))
        assertEquals(PCallkeepConnectionState.STATE_DISCONNECTED, tracker.getState(id))
    }

    // -------------------------------------------------------------------------
    // callId reuse within the same session
    // -------------------------------------------------------------------------

    @Test
    fun `addPending after markTerminated — isTerminated resets to false`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertTrue(tracker.isTerminated("call-1"))

        // Same callId reused for a new incoming call
        tracker.addPending("call-1")
        assertFalse(tracker.isTerminated("call-1"))
        assertTrue(tracker.isPending("call-1"))
    }

    @Test
    fun `promote after markTerminated — isTerminated resets to false`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markAnswered("call-1")
        tracker.markTerminated("call-1")
        assertTrue(tracker.isTerminated("call-1"))

        // Same callId re-promoted (push path — no addPending)
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        assertFalse(tracker.isTerminated("call-1"))
        assertFalse(tracker.isAnswered("call-1"))
        assertTrue(tracker.exists("call-1"))
    }

    @Test
    fun `addPending after markTerminated — stale pendingAnswer is cleared`() {
        tracker.addPending("call-1")
        tracker.reserveAnswer("call-1")
        tracker.markTerminated("call-1")

        // Reuse the same callId
        tracker.addPending("call-1")
        assertFalse(tracker.consumeAnswer("call-1"))
    }
}
