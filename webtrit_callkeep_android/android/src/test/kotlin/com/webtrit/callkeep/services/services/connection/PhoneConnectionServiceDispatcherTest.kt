package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.telecom.DisconnectCause
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.services.connection.dispatchers.ConnectionLifecycleAction
import com.webtrit.callkeep.services.services.connection.dispatchers.PhoneConnectionServiceDispatcher
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [PhoneConnectionServiceDispatcher].
 *
 * Verifies that each [ServiceAction] is routed to the correct [PhoneConnection] method,
 * that missing connections produce a [ConnectionPerform.ConnectionNotFound] fallback,
 * and that lifecycle events (TearDown, ServiceDestroyed) clean up all active connections.
 *
 * [ActivityWakelockManager] and [ProximitySensorManager] are mocked so tests run without
 * a real Activity or proximity sensor hardware.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PhoneConnectionServiceDispatcherTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val capturedEvents = mutableListOf<ConnectionPerform>()
    private val captureDispatcher: PerformDispatchHandle = { event, _ -> capturedEvents.add(event) }

    private val wakelockManager: ActivityWakelockManager = mock()
    private val proximitySensorManager: ProximitySensorManager = mock()

    private lateinit var connectionManager: ConnectionManager
    private lateinit var dispatcher: PhoneConnectionServiceDispatcher

    @Before
    fun setUp() {
        ContextHolder.init(context)
        capturedEvents.clear()
        connectionManager = ConnectionManager()
        dispatcher = PhoneConnectionServiceDispatcher(
            connectionManager,
            captureDispatcher,
            wakelockManager,
            proximitySensorManager,
        )
    }

    private fun createRingingConnection(callId: String = "call-1"): PhoneConnection =
        PhoneConnection.createIncomingPhoneConnection(
            context = context,
            dispatcher = captureDispatcher,
            metadata = CallMetadata(callId = callId),
            onDisconnect = {},
        )

    // -------------------------------------------------------------------------
    // HungUpCall
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch HungUpCall calls hungUp on the matching connection`() {
        val conn = spy(createRingingConnection())
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatch(ServiceAction.HungUpCall, CallMetadata(callId = "call-1"))

        verify(conn).hungUp()
    }

    @Test
    fun `dispatch HungUpCall for unknown callId dispatches ConnectionNotFound`() {
        dispatcher.dispatch(ServiceAction.HungUpCall, CallMetadata(callId = "unknown"))

        assertTrue(capturedEvents.contains(ConnectionPerform.ConnectionNotFound))
    }

    // -------------------------------------------------------------------------
    // DeclineCall
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch DeclineCall calls declineCall on the matching connection`() {
        val conn = spy(createRingingConnection())
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatch(ServiceAction.DeclineCall, CallMetadata(callId = "call-1"))

        verify(conn).declineCall()
    }

    @Test
    fun `dispatch DeclineCall for unknown callId dispatches ConnectionNotFound`() {
        dispatcher.dispatch(ServiceAction.DeclineCall, CallMetadata(callId = "ghost"))

        assertTrue(capturedEvents.contains(ConnectionPerform.ConnectionNotFound))
    }

    // -------------------------------------------------------------------------
    // AnswerCall
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch AnswerCall calls onAnswer on the matching connection`() {
        val conn = spy(createRingingConnection())
        // onAnswer() calls ActivityHolder.start() which tries context.startActivity(null) in
        // Robolectric because no launch activity is registered -- stub it to avoid the NPE.
        doNothing().`when`(conn).onAnswer()
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatch(ServiceAction.AnswerCall, CallMetadata(callId = "call-1"))

        verify(conn).onAnswer()
    }

    @Test
    fun `dispatch AnswerCall for unknown callId dispatches ConnectionNotFound`() {
        dispatcher.dispatch(ServiceAction.AnswerCall, CallMetadata(callId = "ghost"))

        assertTrue(capturedEvents.contains(ConnectionPerform.ConnectionNotFound))
    }

    // -------------------------------------------------------------------------
    // Muting
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch Muting true calls changeMuteState with true`() {
        val conn = spy(createRingingConnection())
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatch(ServiceAction.Muting, CallMetadata(callId = "call-1", hasMute = true))

        verify(conn).changeMuteState(true)
    }

    @Test
    fun `dispatch Muting false calls changeMuteState with false`() {
        val conn = spy(createRingingConnection())
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatch(ServiceAction.Muting, CallMetadata(callId = "call-1", hasMute = false))

        verify(conn).changeMuteState(false)
    }

    // -------------------------------------------------------------------------
    // TearDown
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch TearDown calls hungUp on every active connection`() {
        val conn1 = spy(createRingingConnection("call-1"))
        val conn2 = spy(createRingingConnection("call-2"))
        connectionManager.addConnection("call-1", conn1)
        connectionManager.addConnection("call-2", conn2)

        dispatcher.dispatch(ServiceAction.TearDown, null)

        verify(conn1).hungUp()
        verify(conn2).hungUp()
    }

    @Test
    fun `dispatch TearDown with no connections does not throw`() {
        dispatcher.dispatch(ServiceAction.TearDown, null)
        // no assertion needed -- must not throw
    }

    // -------------------------------------------------------------------------
    // TearDown skips already-disconnected connections
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch TearDown skips connections that are already disconnected`() {
        val conn = spy(createRingingConnection())
        conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        connectionManager.addConnection("call-1", conn)

        // getConnections() filters out STATE_DISCONNECTED -- hungUp must not be called again
        dispatcher.dispatch(ServiceAction.TearDown, null)

        verify(conn, org.mockito.Mockito.never()).hungUp()
    }

    // -------------------------------------------------------------------------
    // ConnectionNotFound fallback -- same callId, connection missing from manager
    // -------------------------------------------------------------------------

    @Test
    fun `ConnectionNotFound is dispatched exactly once for a single missing-connection action`() {
        dispatcher.dispatch(ServiceAction.HungUpCall, CallMetadata(callId = "no-such-call"))

        assertEquals(1, capturedEvents.count { it == ConnectionPerform.ConnectionNotFound })
    }

    // -------------------------------------------------------------------------
    // Lifecycle: ServiceDestroyed
    // -------------------------------------------------------------------------

    @Test
    fun `dispatchLifecycle ServiceDestroyed calls hungUp on all active connections`() {
        val conn = spy(createRingingConnection())
        connectionManager.addConnection("call-1", conn)

        dispatcher.dispatchLifecycle(ConnectionLifecycleAction.ServiceDestroyed)

        verify(conn).hungUp()
    }

    @Test
    fun `dispatchLifecycle ServiceDestroyed with no connections does not throw`() {
        dispatcher.dispatchLifecycle(ConnectionLifecycleAction.ServiceDestroyed)
        // must not throw
    }
}
