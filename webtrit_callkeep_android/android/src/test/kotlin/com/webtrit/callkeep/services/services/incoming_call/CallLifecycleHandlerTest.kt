package com.webtrit.callkeep.services.services.incoming_call

import android.content.Context
import android.os.Build
import android.os.Looper
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.SignalingStatus
import com.webtrit.callkeep.services.broadcaster.SignalingStatusState
import com.webtrit.callkeep.services.services.incoming_call.handlers.CallLifecycleHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for [CallLifecycleHandler].
 *
 * Verifies the decline teardown ordering: performEndCall() must invoke the Flutter-side
 * BYE *before* release() stops the service. In the new design, releaseResources is no
 * longer part of FlutterIsolateCommunicator — the Dart isolate manages its own resource
 * cleanup. release() now calls stopServiceWithDelay() directly.
 *
 * Uses hand-written fakes for [FlutterIsolateCommunicator] to record call order without
 * requiring the mockito-kotlin extension library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CallLifecycleHandlerTest {
    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    /**
     * Records every call made to [FlutterIsolateCommunicator] and lets tests
     * control whether [performEndCall] triggers onSuccess or onFailure.
     */
    private class FakeCommunicator(
        private val endCallResult: EndCallResult = EndCallResult.SUCCESS,
    ) : FlutterIsolateCommunicator {
        enum class EndCallResult { SUCCESS, FAILURE }

        val events = mutableListOf<String>()
        var lastPerformEndCallId: String? = null

        override fun performAnswer(
            callId: String,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            events.add("performAnswer")
            onSuccess()
        }

        override fun performEndCall(
            callId: String,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            events.add("performEndCall")
            lastPerformEndCallId = callId
            when (endCallResult) {
                EndCallResult.SUCCESS -> onSuccess()
                EndCallResult.FAILURE -> onFailure(RuntimeException("BYE failed"))
            }
        }

        override fun syncPushIsolate(
            callData: com.webtrit.callkeep.PCallkeepIncomingCallData?,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            events.add("syncPushIsolate")
            onSuccess()
        }
    }

    // -------------------------------------------------------------------------
    // Fakes (continued)
    // -------------------------------------------------------------------------

    /**
     * Records calls to [CallConnectionController] without Mockito argument matchers,
     * avoiding Kotlin non-null / Mockito.any() NPE issues.
     */
    private class FakeConnectionController : CallConnectionController {
        var answerCallCount = 0
        var tearDownCallCount = 0

        override fun answer(metadata: CallMetadata) {
            answerCallCount++
        }

        override fun decline(metadata: CallMetadata) {}

        override fun hangUp(metadata: CallMetadata) {}

        override fun tearDown() {
            tearDownCallCount++
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var handler: CallLifecycleHandler
    private lateinit var communicator: FakeCommunicator
    private lateinit var fakeController: FakeConnectionController
    private val stopServiceCalls = mutableListOf<String>()

    @Before
    fun setUp() {
        // Force BACKGROUND isolate so executeIfBackground always runs the action.
        // Without this, tests can become order-dependent if another test class leaves
        // SignalingStatusState in a CONNECT/CONNECTING (MAIN) state.
        SignalingStatusState.setValue(SignalingStatus.DISCONNECT)

        communicator = FakeCommunicator()
        fakeController = FakeConnectionController()
        handler =
            CallLifecycleHandler(
                connectionController = fakeController,
                stopService = { stopServiceCalls.add("stop") },
                isolateHandler = mock(com.webtrit.callkeep.services.services.incoming_call.handlers.FlutterIsolateHandler::class.java),
            )
        handler.flutterApi = communicator
        handler.currentCallData =
            PCallkeepIncomingCallData(
                callId = "call-1",
                handle = mock(com.webtrit.callkeep.PHandle::class.java),
                displayName = null,
                hasVideo = false,
            )
    }

    // -------------------------------------------------------------------------
    // performEndCall — ordering: BYE first, stopService after delay
    // -------------------------------------------------------------------------

    /**
     * Regression: performEndCall must emit "performEndCall" before triggering
     * release() → stopServiceWithDelay(). The service must not stop before the
     * SIP BYE is sent.
     */
    @Test
    fun `performEndCall fires performEndCall before stopService on success`() {
        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertTrue(
            "performEndCall must be forwarded to Flutter before service stops",
            communicator.events.contains("performEndCall"),
        )

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(
            "stopService must be called after performEndCall succeeds",
            1,
            stopServiceCalls.size,
        )
    }

    @Test
    fun `performEndCall fires performEndCall before stopService on failure`() {
        communicator = FakeCommunicator(FakeCommunicator.EndCallResult.FAILURE)
        handler.flutterApi = communicator

        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertTrue(
            "performEndCall must be forwarded to Flutter even on failure",
            communicator.events.contains("performEndCall"),
        )

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(
            "stopService must still be called when BYE fails",
            1,
            stopServiceCalls.size,
        )
    }

    @Test
    fun `performEndCall passes correct callId to flutterApi`() {
        handler.performEndCall(CallMetadata(callId = "call-99"))

        assertEquals("call-99", communicator.lastPerformEndCallId)
    }

    @Test
    fun `performEndCall triggers stopService exactly once on success`() {
        handler.performEndCall(CallMetadata(callId = "call-1"))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, stopServiceCalls.size)
    }

    @Test
    fun `performEndCall triggers stopService exactly once on failure`() {
        communicator = FakeCommunicator(FakeCommunicator.EndCallResult.FAILURE)
        handler.flutterApi = communicator

        handler.performEndCall(CallMetadata(callId = "call-1"))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, stopServiceCalls.size)
    }

    // -------------------------------------------------------------------------
    // release — answered path: skips performEndCall, calls stopServiceWithDelay
    // -------------------------------------------------------------------------

    /**
     * release() is used for answered-call teardown (handleRelease(answered=true)).
     * It must NOT call performEndCall — the main process handles active-call signaling.
     * It goes directly to stopServiceWithDelay().
     */
    @Test
    fun `release does not call performEndCall`() {
        handler.release()

        assertFalse(
            "release() must not call performEndCall",
            communicator.events.contains("performEndCall"),
        )
    }

    @Test
    fun `release calls stopService after delay`() {
        handler.release()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(
            "release() must call stopService via stopServiceWithDelay",
            1,
            stopServiceCalls.size,
        )
    }

    @Test
    fun `release calls stopService exactly once`() {
        handler.release()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, stopServiceCalls.size)
    }

    // -------------------------------------------------------------------------
    // null flutterApi — graceful degradation (timeout path)
    // -------------------------------------------------------------------------

    @Test
    fun `performEndCall with null flutterApi calls stopService`() {
        handler.flutterApi = null

        handler.performEndCall(CallMetadata(callId = "call-1"))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, stopServiceCalls.size)
    }

    @Test
    fun `release with null flutterApi calls stopService`() {
        handler.flutterApi = null

        handler.release()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, stopServiceCalls.size)
    }

    // -------------------------------------------------------------------------
    // performAnswerCall -- no duplicate answer signal to Telecom
    // -------------------------------------------------------------------------

    /**
     * Regression: performAnswerCall must NOT call connectionController.answer() after
     * Flutter acknowledges the answer. Telecom already confirmed the call is being answered
     * by invoking performAnswerCall; a second answer() call would send a duplicate signal
     * and could trigger a double notification or double-answer state in the connection service.
     */
    @Test
    fun `performAnswerCall does not call connectionController answer on success`() {
        handler.performAnswerCall(CallMetadata(callId = "call-1"))

        assertTrue(
            "performAnswer must be forwarded to Flutter to confirm the background path ran",
            communicator.events.contains("performAnswer"),
        )
        assertEquals(
            "answer() must not be called -- Telecom already confirmed the answer",
            0,
            fakeController.answerCallCount,
        )
    }

    @Test
    fun `performAnswerCall notifies Flutter isolate via performAnswer`() {
        handler.performAnswerCall(CallMetadata(callId = "call-1"))

        assertTrue(
            "performAnswerCall must forward the event to Flutter",
            communicator.events.contains("performAnswer"),
        )
    }

    @Test
    fun `performAnswerCall tears down connection on Flutter answer failure`() {
        val failingCommunicator =
            object : FlutterIsolateCommunicator {
                override fun performAnswer(
                    callId: String,
                    onSuccess: () -> Unit,
                    onFailure: (Throwable) -> Unit,
                ) {
                    onFailure(RuntimeException("answer rejected"))
                }

                override fun performEndCall(
                    callId: String,
                    onSuccess: () -> Unit,
                    onFailure: (Throwable) -> Unit,
                ) {}

                override fun syncPushIsolate(
                    callData: PCallkeepIncomingCallData?,
                    onSuccess: () -> Unit,
                    onFailure: (Throwable) -> Unit,
                ) {}
            }
        handler.flutterApi = failingCommunicator

        handler.performAnswerCall(CallMetadata(callId = "call-1"))

        assertEquals("tearDown() must be called once on answer failure", 1, fakeController.tearDownCallCount)
    }

    @Test
    fun `performAnswerCall with null flutterApi is a no-op and does not throw`() {
        handler.flutterApi = null

        handler.performAnswerCall(CallMetadata(callId = "call-1"))

        assertEquals(0, fakeController.answerCallCount)
        assertEquals(0, fakeController.tearDownCallCount)
    }
}
