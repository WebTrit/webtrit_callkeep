package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.telecom.Connection
import android.telecom.DisconnectCause
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [PhoneConnection.terminateWithCause] idempotency.
 *
 * Covers three scenarios:
 * 1. Normal disconnect — first call triggers full cleanup and dispatches the correct event.
 * 2. Already-disconnected (idempotent) — second call re-dispatches the original event without
 *    repeating cleanup, using the stored [DisconnectCause] as the source of truth.
 * 3. Race condition — remote hangup sets state to [Connection.STATE_DISCONNECTED] before
 *    [terminateWithCause] is invoked; the re-dispatch prevents the 5-second endCall timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PhoneConnectionTerminateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    // Captured dispatcher events — order matters for multi-call assertions.
    private val dispatchedEvents = mutableListOf<ConnectionEvent>()
    private val dispatcher: PerformDispatchHandle = { event, _ -> dispatchedEvents.add(event) }

    // Count of onDisconnectCallback invocations.
    private var disconnectCallbackCount = 0
    private val onDisconnectCallback: (PhoneConnection) -> Unit = { disconnectCallbackCount++ }

    @Before
    fun setUp() {
        // NotificationManager internally accesses ContextHolder; initialize before each test.
        ContextHolder.init(context)
        dispatchedEvents.clear()
        disconnectCallbackCount = 0
    }

    /**
     * Factory that mirrors [PhoneConnectionService] incoming-call setup:
     * state starts as [Connection.STATE_RINGING].
     */
    private fun createRingingConnection(callId: String = "test-call"): PhoneConnection =
        PhoneConnection.createIncomingPhoneConnection(
            context = context,
            dispatcher = dispatcher,
            metadata = CallMetadata(callId = callId),
            onDisconnect = onDisconnectCallback,
        )

    // -------------------------------------------------------------------------
    // Group 1: Normal disconnect (state != STATE_DISCONNECTED)
    // -------------------------------------------------------------------------

    @Test
    fun `terminateWithCause LOCAL dispatches HungUp`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(listOf(CallLifecycleEvent.HungUp), dispatchedEvents)
    }

    @Test
    fun `terminateWithCause REMOTE dispatches DeclineCall`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.REMOTE))

        assertEquals(listOf(CallLifecycleEvent.DeclineCall), dispatchedEvents)
    }

    @Test
    fun `terminateWithCause REJECTED dispatches HungUp not DeclineCall`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.REJECTED))

        // REJECTED code != REMOTE → falls into the HungUp branch
        assertEquals(listOf(CallLifecycleEvent.HungUp), dispatchedEvents)
    }

    @Test
    fun `terminateWithCause transitions state to DISCONNECTED`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(Connection.STATE_DISCONNECTED, connection.state)
    }

    @Test
    fun `terminateWithCause invokes onDisconnectCallback exactly once`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(1, disconnectCallbackCount)
    }

    // -------------------------------------------------------------------------
    // Group 2: Already-disconnected path (idempotency)
    //
    // Uses setDisconnected() to force STATE_DISCONNECTED without going through
    // the full onDisconnect() cleanup.  This isolates the else-branch behaviour.
    // -------------------------------------------------------------------------

    @Test
    fun `terminateWithCause when already DISCONNECTED with LOCAL stored — re-dispatches HungUp`() {
        val connection = createRingingConnection()
        connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(listOf(CallLifecycleEvent.HungUp), dispatchedEvents)
    }

    @Test
    fun `terminateWithCause when already DISCONNECTED with REMOTE stored — re-dispatches DeclineCall`() {
        val connection = createRingingConnection()
        connection.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))

        // Incoming cause is LOCAL, but stored cause is REMOTE — stored cause wins
        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(listOf(CallLifecycleEvent.DeclineCall), dispatchedEvents)
    }

    @Test
    fun `double terminate — dispatcher called twice, onDisconnectCallback called once`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL)) // full path
        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL)) // else branch

        assertEquals(2, dispatchedEvents.size)
        assertTrue(dispatchedEvents.all { it == CallLifecycleEvent.HungUp })
        assertEquals("onDisconnectCallback must fire only once", 1, disconnectCallbackCount)
    }

    @Test
    fun `stored REMOTE cause wins when second call arrives with LOCAL`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.REMOTE)) // stores REMOTE
        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))  // else branch

        assertEquals(
            "Both dispatches must use the original REMOTE cause",
            listOf(CallLifecycleEvent.DeclineCall, CallLifecycleEvent.DeclineCall),
            dispatchedEvents
        )
    }

    @Test
    fun `stored LOCAL cause wins when second call arrives with REMOTE`() {
        val connection = createRingingConnection()

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))  // stores LOCAL
        connection.terminateWithCause(DisconnectCause(DisconnectCause.REMOTE)) // else branch

        assertEquals(
            "Both dispatches must use the original LOCAL cause",
            listOf(CallLifecycleEvent.HungUp, CallLifecycleEvent.HungUp),
            dispatchedEvents
        )
    }

    @Test
    fun `already-disconnected else branch does NOT invoke onDisconnectCallback`() {
        val connection = createRingingConnection()
        // Force state without going through onDisconnect()
        connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals("onDisconnect cleanup must not repeat", 0, disconnectCallbackCount)
    }

    // -------------------------------------------------------------------------
    // Group 3: Race condition — the core bug
    //
    // Scenario: remote party hangs up → Telecom sets state to STATE_DISCONNECTED
    // (stores the cause) before Flutter's endCall registers its BroadcastReceiver.
    // Old code: terminateWithCause saw disconnected==true → no broadcast → 5 s timeout.
    // New code: else branch re-dispatches using stored cause → instant confirmation.
    // -------------------------------------------------------------------------

    @Test
    fun `race — remote hangup before endCall — broadcast dispatched immediately`() {
        val connection = createRingingConnection()

        // TIME 0: Telecom notifies remote hangup, sets state to DISCONNECTED with REMOTE cause.
        //         The HungUp/DeclineCall broadcast fired here, but Flutter's endCall receiver
        //         was not registered yet (it registers later during endCall processing).
        connection.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))

        // TIME 1: Flutter's endCall arrives and calls terminateWithCause.
        //         Receiver is now registered, but the original broadcast was already missed.
        //         terminateWithCause must re-dispatch so the receiver fires immediately.
        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        // Broadcast fired — no 5-second timeout, endCall resolves immediately.
        assertEquals(listOf(CallLifecycleEvent.DeclineCall), dispatchedEvents)
    }

    @Test
    fun `race — remote hangup before endCall — onDisconnectCallback not re-triggered`() {
        val connection = createRingingConnection()

        connection.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        // onDisconnect() was never called (we used setDisconnected directly above),
        // and the else branch must not call it either.
        assertEquals(0, disconnectCallbackCount)
    }

    @Test
    fun `race — local-hang-up stored before endCall arrives — HungUp dispatched`() {
        val connection = createRingingConnection()

        // Remote side hung up, but we model it as LOCAL this time (e.g. timeout-triggered)
        connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))

        connection.terminateWithCause(DisconnectCause(DisconnectCause.LOCAL))

        assertEquals(listOf(CallLifecycleEvent.HungUp), dispatchedEvents)
    }
}
