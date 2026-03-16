package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.telecom.Connection
import android.telecom.DisconnectCause
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [ConnectionManager].
 *
 * Covers connection storage, state-based queries, duplicate guards,
 * and the [ConnectionManager.validateConnectionAddition] factory logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ConnectionManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val noop: PerformDispatchHandle = { _, _ -> }
    private val noopCallback: (PhoneConnection) -> Unit = {}

    private fun createManager() = ConnectionManager()

    private fun createRingingConnection(callId: String = "call-1"): PhoneConnection =
        PhoneConnection.createIncomingPhoneConnection(
            context = context,
            dispatcher = noop,
            metadata = CallMetadata(callId = callId),
            onDisconnect = noopCallback,
        )

    @Before
    fun setUp() {
        ContextHolder.init(context)
        // Reset the global manager used by validateConnectionAddition
        PhoneConnectionService.connectionManager = createManager()
    }

    // -------------------------------------------------------------------------
    // addConnection / getConnection
    // -------------------------------------------------------------------------

    @Test
    fun `addConnection stores connection retrievable by callId`() {
        val manager = createManager()
        val conn = createRingingConnection()

        manager.addConnection("call-1", conn)

        assertSame(conn, manager.getConnection("call-1"))
    }

    @Test
    fun `getConnection returns null for unknown callId`() {
        assertNull(createManager().getConnection("missing"))
    }

    @Test
    fun `addConnection does not overwrite an existing entry for the same callId`() {
        val manager = createManager()
        val first = createRingingConnection()
        val second = createRingingConnection()

        manager.addConnection("call-1", first)
        manager.addConnection("call-1", second)

        assertSame("first connection must be preserved", first, manager.getConnection("call-1"))
    }

    // -------------------------------------------------------------------------
    // isConnectionAlreadyExists
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionAlreadyExists returns true after add`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection())
        assertTrue(manager.isConnectionAlreadyExists("call-1"))
    }

    @Test
    fun `isConnectionAlreadyExists returns false for unknown callId`() {
        assertFalse(createManager().isConnectionAlreadyExists("unknown"))
    }

    // -------------------------------------------------------------------------
    // isExistsIncomingConnection
    // -------------------------------------------------------------------------

    @Test
    fun `isExistsIncomingConnection returns true when a ringing connection is present`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection())
        assertTrue(manager.isExistsIncomingConnection())
    }

    @Test
    fun `isExistsIncomingConnection returns false when manager is empty`() {
        assertFalse(createManager().isExistsIncomingConnection())
    }

    @Test
    fun `isExistsIncomingConnection returns false when all connections are disconnected`() {
        val manager = createManager()
        val conn = createRingingConnection()
        manager.addConnection("call-1", conn)
        conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

        assertFalse(manager.isExistsIncomingConnection())
    }

    // -------------------------------------------------------------------------
    // isConnectionDisconnected
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionDisconnected returns false for a ringing connection`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection())
        assertFalse(manager.isConnectionDisconnected("call-1"))
    }

    @Test
    fun `isConnectionDisconnected returns true after setDisconnected`() {
        val manager = createManager()
        val conn = createRingingConnection()
        manager.addConnection("call-1", conn)
        conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        assertTrue(manager.isConnectionDisconnected("call-1"))
    }

    // -------------------------------------------------------------------------
    // getConnections (active only)
    // -------------------------------------------------------------------------

    @Test
    fun `getConnections excludes disconnected connections`() {
        val manager = createManager()
        val conn = createRingingConnection()
        manager.addConnection("call-1", conn)
        conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

        assertTrue("disconnected connection must not appear in active list", manager.getConnections().isEmpty())
    }

    @Test
    fun `getConnections includes ringing connection`() {
        val manager = createManager()
        val conn = createRingingConnection()
        manager.addConnection("call-1", conn)

        assertEquals(1, manager.getConnections().size)
        assertSame(conn, manager.getConnections().first())
    }

    // -------------------------------------------------------------------------
    // getActiveConnection
    // -------------------------------------------------------------------------

    @Test
    fun `getActiveConnection returns null when no connection is in STATE_ACTIVE`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection())
        assertNull(manager.getActiveConnection())
    }

    // -------------------------------------------------------------------------
    // isConnectionAnswered
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionAnswered returns false for a freshly created connection`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection())
        assertFalse(manager.isConnectionAnswered("call-1"))
    }

    @Test
    fun `isConnectionAnswered returns false for unknown callId`() {
        assertFalse(createManager().isConnectionAnswered("unknown"))
    }

    // -------------------------------------------------------------------------
    // cleanConnections
    // -------------------------------------------------------------------------

    @Test
    fun `cleanConnections empties the manager`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection("call-1"))
        manager.addConnection("call-2", createRingingConnection("call-2"))

        manager.cleanConnections()

        assertFalse(manager.isConnectionAlreadyExists("call-1"))
        assertFalse(manager.isConnectionAlreadyExists("call-2"))
    }

    // -------------------------------------------------------------------------
    // validateConnectionAddition
    // -------------------------------------------------------------------------

    @Test
    fun `validateConnectionAddition calls onSuccess for a brand-new callId`() {
        var success = false
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = "new-call"),
            onSuccess = { success = true },
            onError = { },
        )
        assertTrue(success)
    }

    @Test
    fun `validateConnectionAddition calls onError TERMINATED for a disconnected callId`() {
        val manager = createManager()
        val conn = createRingingConnection("call-1")
        manager.addConnection("call-1", conn)
        conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        PhoneConnectionService.connectionManager = manager

        var errorEnum: PIncomingCallErrorEnum? = null
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = "call-1"),
            onSuccess = { },
            onError = { errorEnum = it.value },
        )

        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_TERMINATED, errorEnum)
    }

    @Test
    fun `validateConnectionAddition calls onError ALREADY_EXISTS for an active ringing callId`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection("call-1"))
        PhoneConnectionService.connectionManager = manager

        var errorEnum: PIncomingCallErrorEnum? = null
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = "call-1"),
            onSuccess = { },
            onError = { errorEnum = it.value },
        )

        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS, errorEnum)
    }

    @Test
    fun `validateConnectionAddition error result is non-null`() {
        val manager = createManager()
        manager.addConnection("call-1", createRingingConnection("call-1"))
        PhoneConnectionService.connectionManager = manager

        var errorResult: com.webtrit.callkeep.PIncomingCallError? = null
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = "call-1"),
            onSuccess = { },
            onError = { errorResult = it },
        )

        assertNotNull(errorResult)
    }

    // -------------------------------------------------------------------------
    // pendingCallIds / removePending — regression for push-then-main-process bug
    // -------------------------------------------------------------------------

    /**
     * Regression: onCreateIncomingConnection must call removePending() after addConnection().
     *
     * Scenario:
     *  1. Push isolate calls reportNewIncomingCall → checkAndReservePending adds callId to
     *     pendingCallIds and returns null (success).
     *  2. onCreateIncomingConnection fires → addConnection + removePending (the fix).
     *  3. Push isolate auto-answers → hasAnswered = true.
     *  4. Main process CallBloc calls reportNewIncomingCall again (e.g. ~6 s later).
     *
     * Expected: checkAndReservePending sees callId in connections with hasAnswered=true and
     * returns CALL_ID_ALREADY_EXISTS_AND_ANSWERED, NOT CALL_ID_ALREADY_EXISTS.
     *
     * Without the fix (removePending never called), the callId is still in pendingCallIds so
     * the first branch of the when-expression fires and returns CALL_ID_ALREADY_EXISTS —
     * causing Flutter to treat the second report as a generic duplicate instead of recognising
     * that the call is already active and answered.
     */
    @Test
    fun `checkAndReservePending returns ALREADY_EXISTS_AND_ANSWERED after pending is removed and connection answered`() {
        val manager = createManager()
        val callId = "call-push-1"

        // Step 1: push isolate reserves the callId as pending.
        val reserveResult = manager.checkAndReservePending(callId)
        assertNull("initial reservation must succeed", reserveResult)

        // Step 2: onCreateIncomingConnection fires — add connection then remove pending (the fix).
        val conn = createRingingConnection(callId)
        manager.addConnection(callId, conn)
        manager.removePending(callId)  // ← this is the fix under test

        // Step 3: push isolate answers the call.
        conn.onAnswer()
        assertTrue("connection must be marked as answered", manager.isConnectionAnswered(callId))

        // Step 4: main process CallBloc calls reportNewIncomingCall again.
        var errorEnum: PIncomingCallErrorEnum? = null
        PhoneConnectionService.connectionManager = manager
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = callId),
            onSuccess = { },
            onError = { errorEnum = it.value },
        )

        assertEquals(
            "must return CALL_ID_ALREADY_EXISTS_AND_ANSWERED so Flutter knows the call is active",
            PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED,
            errorEnum,
        )
    }

    /**
     * Verifies the bug that existed before the fix: without removePending(), the second
     * reportNewIncomingCall returns CALL_ID_ALREADY_EXISTS instead of
     * CALL_ID_ALREADY_EXISTS_AND_ANSWERED, even when the connection is answered.
     *
     * This test documents the broken behaviour so the regression is explicit.
     */
    @Test
    fun `checkAndReservePending returns ALREADY_EXISTS (not answered) when pending was NOT removed`() {
        val manager = createManager()
        val callId = "call-push-2"

        // Step 1: reserve as pending.
        val reserveResult = manager.checkAndReservePending(callId)
        assertNull(reserveResult)

        // Step 2 (buggy path): add connection but DO NOT call removePending.
        val conn = createRingingConnection(callId)
        manager.addConnection(callId, conn)
        // removePending intentionally omitted to reproduce the bug.

        // Step 3: answer the call.
        conn.onAnswer()

        // Step 4: second report hits pendingCallIds branch first → wrong error code.
        var errorEnum: PIncomingCallErrorEnum? = null
        PhoneConnectionService.connectionManager = manager
        ConnectionManager.validateConnectionAddition(
            metadata = CallMetadata(callId = callId),
            onSuccess = { },
            onError = { errorEnum = it.value },
        )

        assertEquals(
            "without the fix the pending branch fires first, masking hasAnswered",
            PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS,
            errorEnum,
        )
    }

    // -------------------------------------------------------------------------
    // pendingCallIds — basic behaviour
    // -------------------------------------------------------------------------

    @Test
    fun `checkAndReservePending returns null and isPending returns true after reserve`() {
        val manager = createManager()
        assertNull(manager.checkAndReservePending("call-p"))
        assertTrue(manager.isPending("call-p"))
    }

    @Test
    fun `checkAndReservePending returns ALREADY_EXISTS for a callId reserved twice`() {
        val manager = createManager()
        manager.checkAndReservePending("call-p")
        val second = manager.checkAndReservePending("call-p")
        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS, second)
    }

    @Test
    fun `isPending returns false after removePending`() {
        val manager = createManager()
        manager.checkAndReservePending("call-p")
        manager.removePending("call-p")
        assertFalse(manager.isPending("call-p"))
    }
}
