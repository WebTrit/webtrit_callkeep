package com.webtrit.callkeep.services.core

import android.os.Build
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.models.CallMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [InProcessCallkeepCore.startIncomingCall].
 *
 * Covers the centralized pending-callId lifecycle:
 *   1. Concurrent duplicate (addPending returns false) -> onError(CALL_ID_ALREADY_EXISTS),
 *      router never invoked, the other caller's pending entry is preserved.
 *   2. onSuccess -> pending kept (the connection lifecycle owns it from here).
 *   3. onError callback -> pending drained before the caller's onError lambda runs.
 *   4. Synchronous throw -> pending drained, original throwable re-raised verbatim,
 *      onSuccess/onError NOT invoked (the documented contract — see KDoc on
 *      [CallkeepCore.startIncomingCall]).
 *   5. Drain-once guard under double resolution from a misbehaving backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class InProcessCallkeepCoreTest {
    private lateinit var tracker: MainProcessConnectionTracker
    private lateinit var router: CallServiceRouter
    private lateinit var core: InProcessCallkeepCore

    private fun metadata(callId: String = "call-1") = CallMetadata(callId = callId)

    @Before
    fun setUp() {
        tracker = MainProcessConnectionTracker()
        router = mock(CallServiceRouter::class.java)
        core = InProcessCallkeepCore(tracker = tracker, routerInit = { router })
    }

    // ----------------------------------------------------------------------
    // Concurrent duplicate (addPending returns false)
    // ----------------------------------------------------------------------

    @Test
    fun `startIncomingCall — concurrent duplicate routes to onError and skips router`() {
        // A concurrent first invocation already owns the pending entry.
        tracker.addPending("call-1")

        var receivedError: PIncomingCallError? = null
        var successCalled = false
        core.startIncomingCall(
            metadata(),
            onSuccess = { successCalled = true },
            onError = { receivedError = it },
        )

        verifyNoInteractions(router)
        assertFalse(successCalled)
        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS, receivedError?.value)
        // The first caller's pending entry must remain — we do not strip it.
        assertTrue(tracker.isPending("call-1"))
    }

    // ----------------------------------------------------------------------
    // onSuccess keeps pending
    // ----------------------------------------------------------------------

    @Test
    fun `startIncomingCall — onSuccess keeps pending`() {
        stubRouter { _, onSuccess, _ -> onSuccess() }

        var succeeded = false
        core.startIncomingCall(metadata(), onSuccess = { succeeded = true }, onError = {})

        assertTrue(succeeded)
        // Connection lifecycle (promote / markTerminated) drains pending later — not us.
        assertTrue(tracker.isPending("call-1"))
    }

    // ----------------------------------------------------------------------
    // onError drains pending before caller's lambda
    // ----------------------------------------------------------------------

    @Test
    fun `startIncomingCall — onError drains pending before invoking caller's onError`() {
        val expectedError = PIncomingCallError(PIncomingCallErrorEnum.INTERNAL)
        stubRouter { _, _, onError -> onError(expectedError) }

        var receivedError: PIncomingCallError? = null
        var pendingAtCallback = true
        core.startIncomingCall(
            metadata(),
            onSuccess = {},
            onError = {
                receivedError = it
                pendingAtCallback = tracker.isPending("call-1")
            },
        )

        assertEquals(expectedError, receivedError)
        // Drain must run BEFORE the caller's onError lambda is invoked.
        assertFalse(pendingAtCallback)
        assertFalse(tracker.isPending("call-1"))
    }

    // ----------------------------------------------------------------------
    // Synchronous throw drains + re-raises verbatim
    // ----------------------------------------------------------------------

    @Test
    fun `startIncomingCall — synchronous throw drains pending and re-raises verbatim`() {
        val expectedThrow = IllegalStateException("ContextHolder is not initialized. Call init() first.")
        doThrow(expectedThrow)
            .`when`(router)
            .startIncomingCall(anyMetadata(), anyOnSuccess(), anyOnError())

        var receivedError: PIncomingCallError? = null
        var succeeded = false

        try {
            core.startIncomingCall(
                metadata(),
                onSuccess = { succeeded = true },
                onError = { receivedError = it },
            )
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals(expectedThrow, e)
        }

        // Callbacks bypassed — documented contract: caller must rely on its own safety-net
        // (e.g. ForegroundService's 5 s INCOMING_CALL_CONFIRMATION_TIMEOUT_MS).
        assertFalse(succeeded)
        assertNull(receivedError)
        // Pending drained — a subsequent reportNewIncomingCall for this callId can succeed.
        assertFalse(tracker.isPending("call-1"))
    }

    // ----------------------------------------------------------------------
    // Drain-once guarantee
    // ----------------------------------------------------------------------

    @Test
    fun `startIncomingCall — drain runs at most once on double resolution`() {
        val spyTracker = spy(MainProcessConnectionTracker())
        core = InProcessCallkeepCore(tracker = spyTracker, routerInit = { router })

        stubRouter { _, _, onError ->
            // Misbehaving backend fires onError twice — drain-once guard must hold.
            onError(PIncomingCallError(PIncomingCallErrorEnum.INTERNAL))
            onError(PIncomingCallError(PIncomingCallErrorEnum.INTERNAL))
        }

        core.startIncomingCall(metadata(), onSuccess = {}, onError = {})

        verify(spyTracker, times(1)).removePending("call-1")
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    /**
     * Configures [router].startIncomingCall to dispatch via [handler], which receives the
     * metadata and the two callbacks and can invoke whichever it wants.
     */
    private fun stubRouter(
        handler: (CallMetadata, () -> Unit, (PIncomingCallError?) -> Unit) -> Unit,
    ) {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            handler(
                invocation.arguments[0] as CallMetadata,
                invocation.arguments[1] as () -> Unit,
                invocation.arguments[2] as (PIncomingCallError?) -> Unit,
            )
            null
        }.`when`(router).startIncomingCall(anyMetadata(), anyOnSuccess(), anyOnError())
    }

    // Mockito ArgumentMatchers wrappers. Mockito.any() returns null after registering a
    // matcher in the thread-local stack — but `null as CallMetadata` (concrete non-null Kotlin
    // type) compiles to Intrinsics.checkNotNull and throws at runtime. The trick: route through
    // a type-parameter helper so the compiler emits no concrete null check; the typed null is
    // then handed to the Mockito-inline mock, which intercepts before the receiver method body
    // runs (so Kotlin's checkNotNullParameter never fires either).
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun anyMetadata(): CallMetadata {
        ArgumentMatchers.any<CallMetadata>()
        return uninitialized()
    }

    private fun anyOnSuccess(): () -> Unit {
        ArgumentMatchers.any<() -> Unit>()
        return uninitialized()
    }

    private fun anyOnError(): (PIncomingCallError?) -> Unit {
        ArgumentMatchers.any<(PIncomingCallError?) -> Unit>()
        return uninitialized()
    }
}
