package com.webtrit.callkeep.services.services.incoming_call

import android.content.Context
import android.os.Build
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.models.CallMetadata
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

/**
 * Unit tests for [CallLifecycleHandler].
 *
 * Focuses on the decline teardown ordering fix:
 *   performEndCall() must invoke the Flutter-side BYE *before* release() triggers
 *   releaseResources (WebSocket teardown). The old behaviour called release() directly
 *   from handleRelease(answered=false), closing the WebSocket before BYE could be sent.
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
        var releaseResourcesCallCount = 0

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

        override fun releaseResources(
            callData: com.webtrit.callkeep.PCallkeepIncomingCallData?,
            onComplete: () -> Unit,
        ) {
            events.add("releaseResources")
            releaseResourcesCallCount++
            onComplete()
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
    // performEndCall — ordering: BYE first, release after
    // -------------------------------------------------------------------------

    /**
     * Regression: performEndCall must emit "performEndCall" BEFORE "releaseResources".
     * If the order were reversed, the WebSocket would close before the SIP BYE is sent.
     */
    @Test
    fun `performEndCall fires performEndCall before releaseResources on success`() {
        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertEquals(
            "performEndCall must precede releaseResources",
            listOf("performEndCall", "releaseResources"),
            communicator.events,
        )
    }

    @Test
    fun `performEndCall fires performEndCall before releaseResources on failure`() {
        communicator = FakeCommunicator(FakeCommunicator.EndCallResult.FAILURE)
        handler.flutterApi = communicator

        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertEquals(
            "releaseResources must still fire even when BYE fails",
            listOf("performEndCall", "releaseResources"),
            communicator.events,
        )
    }

    @Test
    fun `performEndCall passes correct callId to flutterApi`() {
        handler.performEndCall(CallMetadata(callId = "call-99"))

        assertEquals("call-99", communicator.lastPerformEndCallId)
    }

    @Test
    fun `performEndCall triggers releaseResources exactly once on success`() {
        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertEquals(1, communicator.releaseResourcesCallCount)
    }

    @Test
    fun `performEndCall triggers releaseResources exactly once on failure`() {
        communicator = FakeCommunicator(FakeCommunicator.EndCallResult.FAILURE)
        handler.flutterApi = communicator

        handler.performEndCall(CallMetadata(callId = "call-1"))

        assertEquals(1, communicator.releaseResourcesCallCount)
    }

    // -------------------------------------------------------------------------
    // release — answered path: skips performEndCall, goes straight to releaseResources
    // -------------------------------------------------------------------------

    /**
     * release() is used for answered-call teardown (handleRelease(answered=true)).
     * It must NOT call performEndCall — the main process handles active-call signaling.
     * It goes directly to releaseResources.
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
    fun `release calls releaseResources`() {
        handler.release()

        assertTrue(
            "release() must call releaseResources",
            communicator.events.contains("releaseResources"),
        )
    }

    @Test
    fun `release calls releaseResources exactly once`() {
        handler.release()

        assertEquals(1, communicator.releaseResourcesCallCount)
    }

    // -------------------------------------------------------------------------
    // null flutterApi — graceful degradation (timeout path)
    // -------------------------------------------------------------------------

    @Test
    fun `performEndCall with null flutterApi calls release and stopService`() {
        handler.flutterApi = null

        handler.performEndCall(CallMetadata(callId = "call-1"))

        // release() falls through to stopService() when flutterApi is null
        assertEquals(1, stopServiceCalls.size)
    }

    @Test
    fun `release with null flutterApi calls stopService`() {
        handler.flutterApi = null

        handler.release()

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

        // Confirm the background path actually ran (Flutter was notified) so the
        // answerCallCount == 0 assertion is not trivially satisfied by a no-op.
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
                ) {
                }

                override fun releaseResources(
                    callData: PCallkeepIncomingCallData?,
                    onComplete: () -> Unit,
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

        // No Flutter notification, no teardown, no crash -- graceful degradation.
        assertEquals(0, fakeController.answerCallCount)
        assertEquals(0, fakeController.tearDownCallCount)
    }
}
