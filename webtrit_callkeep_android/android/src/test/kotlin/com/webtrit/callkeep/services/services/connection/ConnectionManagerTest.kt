package com.webtrit.callkeep.services.services.connection

import android.os.Build
import android.telecom.Connection
import com.webtrit.callkeep.models.CallMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ConnectionManagerTest {

    private lateinit var manager: ConnectionManager

    @Before
    fun setUp() {
        manager = ConnectionManager()
    }

    private fun mockConnection(
        state: Int = Connection.STATE_ACTIVE,
        hasVideo: Boolean = false,
        hasAnswered: Boolean = false,
    ): PhoneConnection {
        val conn = mock(PhoneConnection::class.java)
        org.mockito.Mockito.`when`(conn.state).thenReturn(state)
        org.mockito.Mockito.`when`(conn.hasVideo).thenReturn(hasVideo)
        org.mockito.Mockito.`when`(conn.hasAnswered).thenReturn(hasAnswered)
        return conn
    }

    // -------------------------------------------------------------------------
    // addConnection
    // -------------------------------------------------------------------------

    @Test
    fun `addConnection stores the connection`() {
        val conn = mockConnection()
        manager.addConnection("call-1", conn)
        assertNotNull(manager.getConnection("call-1"))
    }

    @Test
    fun `addConnection ignores duplicate callId`() {
        val first = mockConnection()
        val second = mockConnection()
        manager.addConnection("call-1", first)
        manager.addConnection("call-1", second)
        // first connection wins
        assertEquals(first, manager.getConnection("call-1"))
    }

    // -------------------------------------------------------------------------
    // getConnection
    // -------------------------------------------------------------------------

    @Test
    fun `getConnection returns null for unknown callId`() {
        assertNull(manager.getConnection("unknown"))
    }

    @Test
    fun `getConnection returns stored connection`() {
        val conn = mockConnection()
        manager.addConnection("call-1", conn)
        assertEquals(conn, manager.getConnection("call-1"))
    }

    // -------------------------------------------------------------------------
    // getConnections
    // -------------------------------------------------------------------------

    @Test
    fun `getConnections excludes disconnected connections`() {
        val active = mockConnection(state = Connection.STATE_ACTIVE)
        val disconnected = mockConnection(state = Connection.STATE_DISCONNECTED)
        manager.addConnection("active", active)
        manager.addConnection("disconnected", disconnected)

        val result = manager.getConnections()
        assertEquals(1, result.size)
        assertEquals(active, result.first())
    }

    @Test
    fun `getConnections returns all non-disconnected connections`() {
        val ringing = mockConnection(state = Connection.STATE_RINGING)
        val dialing = mockConnection(state = Connection.STATE_DIALING)
        val active = mockConnection(state = Connection.STATE_ACTIVE)
        manager.addConnection("ringing", ringing)
        manager.addConnection("dialing", dialing)
        manager.addConnection("active", active)

        assertEquals(3, manager.getConnections().size)
    }

    @Test
    fun `getConnections returns empty list when all disconnected`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_DISCONNECTED))
        assertTrue(manager.getConnections().isEmpty())
    }

    // -------------------------------------------------------------------------
    // isConnectionAlreadyExists
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionAlreadyExists returns false for unknown callId`() {
        assertFalse(manager.isConnectionAlreadyExists("unknown"))
    }

    @Test
    fun `isConnectionAlreadyExists returns true after add`() {
        manager.addConnection("call-1", mockConnection())
        assertTrue(manager.isConnectionAlreadyExists("call-1"))
    }

    // -------------------------------------------------------------------------
    // hasVideoConnections
    // -------------------------------------------------------------------------

    @Test
    fun `hasVideoConnections returns false when no connections`() {
        assertFalse(manager.hasVideoConnections())
    }

    @Test
    fun `hasVideoConnections returns false when no video connection`() {
        manager.addConnection("call-1", mockConnection(hasVideo = false))
        assertFalse(manager.hasVideoConnections())
    }

    @Test
    fun `hasVideoConnections returns true when video connection exists`() {
        manager.addConnection("call-video", mockConnection(hasVideo = true))
        manager.addConnection("call-audio", mockConnection(hasVideo = false))
        assertTrue(manager.hasVideoConnections())
    }

    // -------------------------------------------------------------------------
    // isConnectionDisconnected
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionDisconnected returns false for unknown callId`() {
        assertFalse(manager.isConnectionDisconnected("unknown"))
    }

    @Test
    fun `isConnectionDisconnected returns true when state is DISCONNECTED`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_DISCONNECTED))
        assertTrue(manager.isConnectionDisconnected("call-1"))
    }

    @Test
    fun `isConnectionDisconnected returns false when state is ACTIVE`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_ACTIVE))
        assertFalse(manager.isConnectionDisconnected("call-1"))
    }

    // -------------------------------------------------------------------------
    // getActiveConnection
    // -------------------------------------------------------------------------

    @Test
    fun `getActiveConnection returns null when no connections`() {
        assertNull(manager.getActiveConnection())
    }

    @Test
    fun `getActiveConnection returns the active connection`() {
        val active = mockConnection(state = Connection.STATE_ACTIVE)
        val ringing = mockConnection(state = Connection.STATE_RINGING)
        manager.addConnection("active", active)
        manager.addConnection("ringing", ringing)
        assertEquals(active, manager.getActiveConnection())
    }

    @Test
    fun `getActiveConnection returns null when only ringing and dialing`() {
        manager.addConnection("r", mockConnection(state = Connection.STATE_RINGING))
        manager.addConnection("d", mockConnection(state = Connection.STATE_DIALING))
        assertNull(manager.getActiveConnection())
    }

    // -------------------------------------------------------------------------
    // isExistsIncomingConnection
    // -------------------------------------------------------------------------

    @Test
    fun `isExistsIncomingConnection returns false when empty`() {
        assertFalse(manager.isExistsIncomingConnection())
    }

    @Test
    fun `isExistsIncomingConnection returns true for STATE_RINGING`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_RINGING))
        assertTrue(manager.isExistsIncomingConnection())
    }

    @Test
    fun `isExistsIncomingConnection returns true for STATE_NEW`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_NEW))
        assertTrue(manager.isExistsIncomingConnection())
    }

    @Test
    fun `isExistsIncomingConnection returns false for only active connections`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_ACTIVE))
        assertFalse(manager.isExistsIncomingConnection())
    }

    // -------------------------------------------------------------------------
    // isConnectionAnswered
    // -------------------------------------------------------------------------

    @Test
    fun `isConnectionAnswered returns false for unknown callId`() {
        assertFalse(manager.isConnectionAnswered("unknown"))
    }

    @Test
    fun `isConnectionAnswered returns true when connection hasAnswered`() {
        manager.addConnection("call-1", mockConnection(hasAnswered = true))
        assertTrue(manager.isConnectionAnswered("call-1"))
    }

    @Test
    fun `isConnectionAnswered returns false when connection not answered`() {
        manager.addConnection("call-1", mockConnection(hasAnswered = false))
        assertFalse(manager.isConnectionAnswered("call-1"))
    }

    // -------------------------------------------------------------------------
    // cleanConnections
    // -------------------------------------------------------------------------

    @Test
    fun `cleanConnections calls destroy on all connections and clears`() {
        val conn1 = mockConnection()
        val conn2 = mockConnection()
        manager.addConnection("call-1", conn1)
        manager.addConnection("call-2", conn2)

        manager.cleanConnections()

        verify(conn1).destroy()
        verify(conn2).destroy()
        assertTrue(manager.getConnections().isEmpty())
        assertNull(manager.getConnection("call-1"))
        assertNull(manager.getConnection("call-2"))
    }

    @Test
    fun `cleanConnections on empty manager does not throw`() {
        manager.cleanConnections()
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    fun `toString returns non-empty string`() {
        manager.addConnection("call-1", mockConnection(state = Connection.STATE_ACTIVE))
        val str = manager.toString()
        assertNotNull(str)
        assertTrue(str.contains("ConnectionManager"))
    }
}
