package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.telecom.DisconnectCause
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.managers.AudioManager
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the call-waiting audio branching logic (WT-1388).
 *
 * When a second incoming call arrives while one call is already active or held,
 * [PhoneConnection.onShowIncomingCallUi] must play a soft call-waiting tone via
 * [AudioManager.startCallWaitingTone] instead of the full ringtone. The ringtone
 * uses TYPE_RINGTONE which routes through the earpiece at full ringtone volume and
 * can hurt the user's ear during an active call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PhoneConnectionCallWaitingTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val noop: PerformDispatchHandle = { _, _ -> }
    private val noopCallback: (PhoneConnection) -> Unit = {}

    @Before
    fun setUp() {
        ContextHolder.init(context)
        PhoneConnectionService.connectionManager = ConnectionManager()
    }

    private fun createConnectionWithAudio(audioManager: AudioManager): PhoneConnection =
        PhoneConnection(
            context = context,
            dispatcher = noop,
            metadata = CallMetadata(callId = "incoming-2"),
            onDisconnectCallback = noopCallback,
            audioManager = audioManager,
        )

    private fun managerWithActiveCall(): ConnectionManager {
        val manager = ConnectionManager()
        val conn =
            PhoneConnection.createIncomingPhoneConnection(
                context = context,
                dispatcher = noop,
                metadata = CallMetadata(callId = "active-1"),
                onDisconnect = noopCallback,
            )
        conn.setActive()
        manager.addConnection("active-1", conn)
        return manager
    }

    private fun managerWithHeldCall(): ConnectionManager {
        val manager = ConnectionManager()
        val conn =
            PhoneConnection.createIncomingPhoneConnection(
                context = context,
                dispatcher = noop,
                metadata = CallMetadata(callId = "held-1"),
                onDisconnect = noopCallback,
            )
        conn.setOnHold()
        manager.addConnection("held-1", conn)
        return manager
    }

    // -------------------------------------------------------------------------
    // onShowIncomingCallUi — audio routing decision
    // -------------------------------------------------------------------------

    @Test
    fun `onShowIncomingCallUi plays ringtone when no active call exists`() {
        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onShowIncomingCallUi()

        verify(mockAudio).startRingtone(null)
        verify(mockAudio, never()).startCallWaitingTone()
    }

    @Test
    fun `onShowIncomingCallUi plays call-waiting tone when a call is active`() {
        PhoneConnectionService.connectionManager = managerWithActiveCall()
        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onShowIncomingCallUi()

        verify(mockAudio).startCallWaitingTone()
        verify(mockAudio, never()).startRingtone(null)
    }

    @Test
    fun `onShowIncomingCallUi plays call-waiting tone when a call is on hold`() {
        PhoneConnectionService.connectionManager = managerWithHeldCall()
        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onShowIncomingCallUi()

        verify(mockAudio).startCallWaitingTone()
        verify(mockAudio, never()).startRingtone(null)
    }

    @Test
    fun `onShowIncomingCallUi plays ringtone after active call disconnects`() {
        val manager = managerWithActiveCall()
        manager.getActiveConnection()?.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
        PhoneConnectionService.connectionManager = manager

        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onShowIncomingCallUi()

        verify(mockAudio).startRingtone(null)
        verify(mockAudio, never()).startCallWaitingTone()
    }

    // -------------------------------------------------------------------------
    // stopCallWaitingTone — cleanup paths
    // -------------------------------------------------------------------------

    @Test
    fun `onSilence stops the call-waiting tone`() {
        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onSilence()

        verify(mockAudio).stopCallWaitingTone()
    }

    @Test
    fun `onDisconnect stops the call-waiting tone`() {
        val mockAudio = mock(AudioManager::class.java)
        val connection = createConnectionWithAudio(mockAudio)

        connection.onDisconnect()

        verify(mockAudio).stopCallWaitingTone()
    }
}
