package com.webtrit.callkeep.services.services.connection

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.models.CallMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PhoneServiceCommand.from] and [StandaloneServiceCommand.from].
 *
 * The central regression guard: a no-extras lifecycle action delivered with a non-null but empty
 * [Bundle] (as Binder IPC can produce) must parse into a lifecycle command, NOT throw
 * IllegalArgumentException from CallMetadata parsing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ServiceCommandTest {
    private fun intent(
        action: String,
        extras: Bundle?,
    ): Intent =
        Intent().apply {
            this.action = action
            if (extras != null) putExtras(extras)
        }

    // ----------------------------------------------------------------------- Phone

    @Test
    fun phone_lifecycleAction_withEmptyBundle_doesNotCrashAndReturnsLifecycleCommand() {
        val command = PhoneServiceCommand.from(intent(ServiceAction.TearDownConnections.action, Bundle()))
        assertEquals(PhoneServiceCommand.TearDown, command)
    }

    @Test
    fun phone_lifecycleAction_withNullExtras_returnsLifecycleCommand() {
        assertEquals(PhoneServiceCommand.Clean, PhoneServiceCommand.from(intent(ServiceAction.CleanConnections.action, null)))
        assertEquals(PhoneServiceCommand.ReplayAudio, PhoneServiceCommand.from(intent(ServiceAction.ReplayAudioState.action, null)))
        assertEquals(
            PhoneServiceCommand.ReplayConnections,
            PhoneServiceCommand.from(intent(ServiceAction.ReplayConnectionStates.action, null)),
        )
    }

    @Test
    fun phone_reserveAnswer_withCallId_returnsReserve() {
        val extras = Bundle().apply { putString(CallDataConst.CALL_ID, "call-1") }
        assertEquals(PhoneServiceCommand.Reserve("call-1"), PhoneServiceCommand.from(intent(ServiceAction.ReserveAnswer.action, extras)))
    }

    @Test
    fun phone_reserveAnswer_withoutCallId_returnsNull() {
        assertNull(PhoneServiceCommand.from(intent(ServiceAction.ReserveAnswer.action, Bundle())))
    }

    @Test
    fun phone_callOp_withoutExtras_returnsCallOpWithNullMetadata() {
        val command = PhoneServiceCommand.from(intent(ServiceAction.AnswerCall.action, null))
        assertTrue(command is PhoneServiceCommand.CallOp)
        command as PhoneServiceCommand.CallOp
        assertEquals(ServiceAction.AnswerCall, command.action)
        assertNull(command.metadata)
    }

    @Test
    fun phone_unknownAction_returnsNull() {
        assertNull(PhoneServiceCommand.from(intent("callkeep_not_an_action", null)))
    }

    // ----------------------------------------------------------------------- Standalone

    @Test
    fun standalone_lifecycleAction_withEmptyBundle_doesNotCrashAndReturnsLifecycleCommand() {
        val command = StandaloneServiceCommand.from(intent(StandaloneServiceAction.TearDownConnections.action, Bundle()))
        assertEquals(StandaloneServiceCommand.TearDown, command)
    }

    @Test
    fun standalone_callSetupAction_withMetadata_returnsCallMarkedAsSetup() {
        val extras = CallMetadata(callId = "call-3").toBundle()
        val command = StandaloneServiceCommand.from(intent(StandaloneServiceAction.IncomingCall.action, extras))
        assertTrue(command is StandaloneServiceCommand.Call)
        command as StandaloneServiceCommand.Call
        assertEquals(StandaloneServiceAction.IncomingCall, command.action)
    }

    @Test
    fun standalone_callAction_withoutMetadata_returnsNull() {
        assertNull(StandaloneServiceCommand.from(intent(StandaloneServiceAction.AnswerCall.action, Bundle())))
    }

    @Test
    fun standalone_isCallSetup_trueOnlyForIncomingAndOutgoing() {
        // onStartCommand promotes to foreground based on this predicate computed from the raw
        // action — independently of whether the command metadata parses — so a call-setup intent
        // with empty extras still satisfies the startForeground() window.
        val setup = setOf(StandaloneServiceAction.IncomingCall, StandaloneServiceAction.OutgoingCall)
        StandaloneServiceAction.entries.forEach { action ->
            assertEquals(action in setup, action.isCallSetup)
        }
    }

    @Test
    fun standalone_reserveAnswer_withCallId_returnsReserve() {
        val extras = Bundle().apply { putString(CallDataConst.CALL_ID, "call-4") }
        assertEquals(
            StandaloneServiceCommand.Reserve("call-4"),
            StandaloneServiceCommand.from(intent(StandaloneServiceAction.ReserveAnswer.action, extras)),
        )
    }

    @Test
    fun standalone_unknownAction_returnsNull() {
        assertNull(StandaloneServiceCommand.from(intent("not_a_standalone_action", null)))
    }
}
