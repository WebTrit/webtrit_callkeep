package com.webtrit.callkeep.services.services.connection

import android.os.Build
import com.webtrit.callkeep.PIncomingCallError
import com.webtrit.callkeep.PIncomingCallErrorEnum
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.foreground.ForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
/**
 * Tests for [ConnectionManager.validateConnectionAddition].
 *
 * This is the core of the push-answer fix: when the user answers a call from the
 * push notification while the app is in the background, then the app restarts and
 * calls reportNewIncomingCall, validateConnectionAddition must return the correct
 * error code so the Dart bloc can trigger auto-answer at the signaling level.
 */
class ValidateConnectionAdditionTest {

    private val tracker get() = ForegroundService.connectionTracker

    @Before
    fun setUp() {
        tracker.clear()
    }

    private fun meta(callId: String) = CallMetadata(callId = callId)

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    @Test
    fun `new callId calls onSuccess`() {
        var successCalled = false
        var errorReceived: PIncomingCallError? = null

        ConnectionManager.validateConnectionAddition(
            metadata = meta("new-call"),
            onSuccess = { successCalled = true },
            onError = { errorReceived = it }
        )

        assert(successCalled) { "onSuccess should be called for an unknown callId" }
        assertNull(errorReceived)
    }

    // -------------------------------------------------------------------------
    // CALL_ID_ALREADY_EXISTS
    // -------------------------------------------------------------------------

    @Test
    fun `existing callId that is not answered returns CALL_ID_ALREADY_EXISTS`() {
        tracker.add("existing-call", meta("existing-call"))

        var error: PIncomingCallError? = null
        var successCalled = false

        ConnectionManager.validateConnectionAddition(
            metadata = meta("existing-call"),
            onSuccess = { successCalled = true },
            onError = { error = it }
        )

        assert(!successCalled) { "onSuccess must not be called for an existing callId" }
        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS, error?.value)
    }

    // -------------------------------------------------------------------------
    // CALL_ID_ALREADY_EXISTS_AND_ANSWERED
    // -------------------------------------------------------------------------

    @Test
    fun `answered callId returns CALL_ID_ALREADY_EXISTS_AND_ANSWERED`() {
        tracker.add("answered-call", meta("answered-call"))
        tracker.markAnswered("answered-call")

        var error: PIncomingCallError? = null
        var successCalled = false

        ConnectionManager.validateConnectionAddition(
            metadata = meta("answered-call"),
            onSuccess = { successCalled = true },
            onError = { error = it }
        )

        assert(!successCalled) { "onSuccess must not be called for an answered callId" }
        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED, error?.value)
    }

    @Test
    fun `answered callId takes priority over exists check`() {
        // Both exists AND answered — must return the more specific ANSWERED code.
        tracker.add("call-1", meta("call-1"))
        tracker.markAnswered("call-1")

        var error: PIncomingCallError? = null

        ConnectionManager.validateConnectionAddition(
            metadata = meta("call-1"),
            onSuccess = {},
            onError = { error = it }
        )

        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED, error?.value)
    }

    @Test
    fun `answered but removed callId returns success`() {
        // Call was answered, then removed (hung up) — a new reportNewIncomingCall should succeed.
        tracker.add("call-1", meta("call-1"))
        tracker.markAnswered("call-1")
        tracker.remove("call-1")

        var successCalled = false
        var error: PIncomingCallError? = null

        ConnectionManager.validateConnectionAddition(
            metadata = meta("call-1"),
            onSuccess = { successCalled = true },
            onError = { error = it }
        )

        assert(successCalled) { "After remove, new call should succeed" }
        assertNull(error)
    }

    @Test
    fun `different callIds are validated independently`() {
        tracker.add("call-answered", meta("call-answered"))
        tracker.markAnswered("call-answered")

        tracker.add("call-ringing", meta("call-ringing"))

        var answeredError: PIncomingCallError? = null
        var ringingError: PIncomingCallError? = null
        var newSuccess = false

        ConnectionManager.validateConnectionAddition(
            metadata = meta("call-answered"),
            onSuccess = {},
            onError = { answeredError = it }
        )
        ConnectionManager.validateConnectionAddition(
            metadata = meta("call-ringing"),
            onSuccess = {},
            onError = { ringingError = it }
        )
        ConnectionManager.validateConnectionAddition(
            metadata = meta("call-new"),
            onSuccess = { newSuccess = true },
            onError = {}
        )

        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS_AND_ANSWERED, answeredError?.value)
        assertEquals(PIncomingCallErrorEnum.CALL_ID_ALREADY_EXISTS, ringingError?.value)
        assert(newSuccess)
    }
}
