package com.webtrit.callkeep.services.core

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
    private var fakeNow = 0L

    private fun metadata(callId: String = "call-1") = CallMetadata(callId = callId)

    @Before
    fun setUp() {
        fakeNow = 0L
        tracker = MainProcessConnectionTracker(clock = { fakeNow })
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
    fun `addPending — returns true when newly inserted`() {
        assertTrue(tracker.addPending("call-1"))
    }

    @Test
    fun `addPending — returns false when already pending`() {
        tracker.addPending("call-1")
        assertFalse(tracker.addPending("call-1"))
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
    // getPendingCallIds
    // -------------------------------------------------------------------------

    @Test
    fun `getPendingCallIds — returns all pending callIds`() {
        tracker.addPending("call-1")
        tracker.addPending("call-2")
        assertEquals(setOf("call-1", "call-2"), tracker.getPendingCallIds())
    }

    @Test
    fun `getPendingCallIds — does not include promoted calls`() {
        tracker.addPending("call-pending")
        tracker.addPending("call-promoted")
        tracker.promote("call-promoted", metadata("call-promoted"), PCallkeepConnectionState.STATE_RINGING)
        assertFalse(tracker.getPendingCallIds().contains("call-promoted"))
        assertTrue(tracker.getPendingCallIds().contains("call-pending"))
    }

    @Test
    fun `getPendingCallIds — is non-destructive`() {
        tracker.addPending("call-1")
        tracker.getPendingCallIds()
        assertTrue(tracker.isPending("call-1"))
        assertEquals(setOf("call-1"), tracker.getPendingCallIds())
    }

    @Test
    fun `getPendingCallIds — empty after clear`() {
        tracker.addPending("call-1")
        tracker.clear()
        assertTrue(tracker.getPendingCallIds().isEmpty())
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
        assertFalse(tracker.exists(id)) // not yet promoted, so not in connections

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

    // -------------------------------------------------------------------------
    // cold-start race: markAnswered before addPending/promote
    //
    // Reproduces the scenario where SyncConnectionState fires
    // handleCSReportAnswerCall during ForegroundService.onCreate (marking the
    // call answered via markAnswered) before reportNewIncomingCall arrives
    // from the signaling layer and calls addPending/promote.
    //
    // ForegroundService.reportNewIncomingCall checks isAnswered() in its early
    // guard and must see true so that the CALL_ID_ALREADY_EXISTS_AND_ANSWERED
    // branch fires. The fix then calls promote() + markAnswered() + performAnswerCall
    // to adopt the call without another Telecom round-trip.
    // -------------------------------------------------------------------------

    @Test
    fun `cold-start — markAnswered without prior promote — isAnswered returns true`() {
        // SyncConnectionState calls markAnswered before the call is registered in the
        // tracker. The early check in reportNewIncomingCall must detect this.
        tracker.markAnswered("call-1")
        assertTrue(tracker.isAnswered("call-1"))
    }

    @Test
    fun `cold-start — markAnswered without prior promote — exists returns false`() {
        // The call is answered in Telecom but not yet in the tracker's connections map.
        // reportNewIncomingCall's early check uses isAnswered(), not exists().
        tracker.markAnswered("call-1")
        assertFalse(tracker.exists("call-1"))
    }

    @Test
    fun `cold-start — markAnswered without prior promote — isPending returns false`() {
        tracker.markAnswered("call-1")
        assertFalse(tracker.isPending("call-1"))
    }

    @Test
    fun `cold-start — markAnswered without prior promote — getAll returns empty`() {
        // Call is not in connections yet; tearDown's getAll() would miss it if
        // the fix does not call promote() afterward.
        tracker.markAnswered("call-1")
        assertTrue(tracker.getAll().isEmpty())
    }

    @Test
    fun `cold-start — promote after markAnswered clears isAnswered — must re-mark`() {
        // promote() resets answeredCallIds per its existing contract (guards against
        // stale answered state on callId reuse). The fix must call markAnswered()
        // AFTER promote() to restore the answered flag.
        tracker.markAnswered("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_ACTIVE)

        assertTrue(tracker.exists("call-1"))
        assertFalse(tracker.isAnswered("call-1")) // cleared by promote — caller must re-mark
        assertEquals(PCallkeepConnectionState.STATE_ACTIVE, tracker.getState("call-1"))
    }

    @Test
    fun `cold-start — full fix sequence — promote then markAnswered leaves tracker consistent`() {
        // Verifies the exact sequence executed by the ALREADY_ANSWERED branch fix:
        //   1. markAnswered()           <- SyncConnectionState (cold-start)
        //   2. promote(STATE_ACTIVE)    <- fix step 1
        //   3. markAnswered()           <- fix step 2 (re-mark after promote clears it)
        //   4. markSignalingRegistered()  <- fix step 3
        tracker.markAnswered("call-1") // cold-start
        assertTrue(tracker.isAnswered("call-1"))

        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_ACTIVE) // fix 1
        tracker.markAnswered("call-1") // fix 2
        tracker.markSignalingRegistered("call-1") // fix 3

        assertTrue(tracker.exists("call-1"))
        assertTrue(tracker.isAnswered("call-1"))
        assertFalse(tracker.isPending("call-1"))
        assertEquals(PCallkeepConnectionState.STATE_ACTIVE, tracker.getState("call-1"))
        // DidPushIncomingCall broadcast must be suppressed after adoption
        assertTrue(tracker.consumeSignalingRegistered("call-1"))
    }

    @Test
    fun `cold-start — endCall can find adopted call via exists()`() {
        // Regression guard: before the fix, promote() was never called in the
        // ALREADY_ANSWERED branch so exists() was false and endCall() could not
        // locate the call ("no connection or pending entry").
        tracker.markAnswered("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_ACTIVE)
        tracker.markAnswered("call-1")

        assertTrue(tracker.exists("call-1"))

        tracker.markTerminated("call-1")

        assertFalse(tracker.exists("call-1"))
        assertTrue(tracker.isTerminated("call-1"))
        assertEquals(PCallkeepConnectionState.STATE_DISCONNECTED, tracker.getState("call-1"))
    }

    @Test
    fun `cold-start — getAll includes adopted call for tearDown`() {
        // After adoption, getAll() must return the call so tearDown fires performEndCall.
        tracker.markAnswered("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_ACTIVE)
        tracker.markAnswered("call-1")

        val all = tracker.getAll()
        assertEquals(1, all.size)
        assertEquals("call-1", all.first().callId)
    }

    @Test
    fun `cold-start — drainUnconnectedPendingCallIds does not include adopted call`() {
        // promote() moves the call out of pendingCallIds, so drain() must not
        // return it — preventing a spurious second performEndCall during tearDown.
        tracker.markAnswered("call-1")
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_ACTIVE)
        tracker.markAnswered("call-1")

        val drained = tracker.drainUnconnectedPendingCallIds()
        assertFalse(drained.contains("call-1"))
    }

    // -------------------------------------------------------------------------
    // terminatedCallIds TTL (10 s auto-cleanup)
    // -------------------------------------------------------------------------

    @Test
    fun `isTerminated — returns true immediately after markTerminated`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        assertTrue(tracker.isTerminated("call-1"))
    }

    @Test
    fun `isTerminated — returns true just before TTL expires`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        fakeNow = 9_999L
        assertTrue(tracker.isTerminated("call-1"))
    }

    @Test
    fun `isTerminated — returns false exactly at TTL boundary`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        fakeNow = 10_000L
        assertFalse(tracker.isTerminated("call-1"))
    }

    @Test
    fun `isTerminated — returns false after TTL has passed`() {
        tracker.promote("call-1", metadata(), PCallkeepConnectionState.STATE_RINGING)
        tracker.markTerminated("call-1")
        fakeNow = 15_000L
        assertFalse(tracker.isTerminated("call-1"))
    }

    @Test
    fun `isTerminated — evicts entry on expiry so repeated calls return false`() {
        tracker.markTerminated("call-1")
        fakeNow = 10_000L
        assertFalse(tracker.isTerminated("call-1"))
        // Second call must also return false (entry was removed on first expiry read)
        assertFalse(tracker.isTerminated("call-1"))
    }

    @Test
    fun `isTerminated — independent TTLs per call`() {
        tracker.markTerminated("call-1")
        fakeNow = 5_000L
        tracker.markTerminated("call-2")

        fakeNow = 10_000L
        // call-1 terminated at t=0, now t=10_000 -> expired
        assertFalse(tracker.isTerminated("call-1"))
        // call-2 terminated at t=5_000, now t=10_000 -> 5 s elapsed -> still valid
        assertTrue(tracker.isTerminated("call-2"))
    }
}
