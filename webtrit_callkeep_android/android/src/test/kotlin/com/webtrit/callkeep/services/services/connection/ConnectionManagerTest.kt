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
        // Reset the global manager and pending set used by validateConnectionAddition
        PhoneConnectionService.connectionManager = createManager()
        PhoneConnectionService.pendingCallIds.clear()
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
}
