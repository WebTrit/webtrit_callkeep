package com.webtrit.callkeep.services.services.connection

import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.telecom.CallEndpoint
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.models.PerformDispatchHandle
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Behavior Tests for [PhoneConnection].
 *
 * Verifies the business logic of the connection service, specifically:
 * 1. Interaction with [CallMetadata] configuration (speakerOnVideo).
 * 2. Execution of enforcement logic (enforceVideoSpeakerLogic).
 * 3. Interactions with system components (simulated via Spy).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PhoneConnectionTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val dispatcher: PerformDispatchHandle = mock()
    private val onDisconnect: (PhoneConnection) -> Unit = mock()

    /**
     * Helper to create a Spy object around a real PhoneConnection.
     * Allows verification of method calls like `toggleSpeaker` while running real logic.
     */
    private fun createConnection(metadata: CallMetadata): PhoneConnection {
        val realConnection = PhoneConnection(context, dispatcher, metadata, onDisconnect)
        return spy(realConnection)
    }

    /**
     * Helper to populate [PhoneConnection.availableCallEndpoints] so the
     * enforcement logic doesn't exit early on API 34+.
     */
    private fun simulateEndpoints(connection: PhoneConnection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Create dummy endpoints (Speaker and Earpiece)
            val earpiece =
                CallEndpoint(
                    "Earpiece",
                    CallEndpoint.TYPE_EARPIECE,
                    ParcelUuid(UUID.randomUUID()),
                )
            val speaker =
                CallEndpoint(
                    "Speaker",
                    CallEndpoint.TYPE_SPEAKER,
                    ParcelUuid(UUID.randomUUID()),
                )

            // Push them to the connection
            connection.onAvailableCallEndpointsChanged(listOf(earpiece, speaker))
        }
    }

    /**
     * Scenario: Default Configuration.
     * Metadata has `speakerOnVideo = null` (system default).
     * When upgrading to Video, the speaker MUST be enabled effectively (logic treats null as true).
     */
    @Test
    fun `updateData with Video TRUE and Config NULL (Default) enforces speaker`() {
        // 1. Start with audio-only call
        val initial = CallMetadata(callId = "test-default", hasVideo = false)
        val connection = createConnection(initial)

        // Provide endpoints so the logic proceeds
        simulateEndpoints(connection)

        // Update to VIDEO. Config is NULL (system default).
        val update = CallMetadata(callId = "test-default", hasVideo = true)

        connection.updateData(update)

        verify(connection).toggleSpeaker(true)
    }

    /**
     * Scenario: Explicit Disable.
     * Metadata has `speakerOnVideo = false`.
     * When upgrading to Video, the speaker MUST NOT be enabled, respecting the configuration.
     */
    @Test
    fun `updateData with Video TRUE and Config FALSE ignores speaker`() {
        // Start with audio call, Explicitly DISABLE speaker config
        val initial =
            CallMetadata(
                callId = "test-disabled",
                hasVideo = false,
                speakerOnVideo = false,
            )
        val connection = createConnection(initial)
        simulateEndpoints(connection)

        // Update to VIDEO. Config remains FALSE (preserved via merge logic).
        val update = CallMetadata(callId = "test-disabled", hasVideo = true)

        connection.updateData(update)

        verify(connection, never()).toggleSpeaker(true)
    }

    /**
     * Scenario: Explicit Enable.
     * Metadata has `speakerOnVideo = true`.
     * When upgrading to Video, the speaker MUST be enabled.
     */
    @Test
    fun `updateData with Video TRUE and Config TRUE enforces speaker`() {
        val initial = CallMetadata(callId = "test-enabled", hasVideo = false)
        val connection = createConnection(initial)
        simulateEndpoints(connection)

        // Update to VIDEO with Explicit ENABLE.
        val update =
            CallMetadata(
                callId = "test-enabled",
                hasVideo = true,
                speakerOnVideo = true,
            )

        connection.updateData(update)

        verify(connection).toggleSpeaker(true)
    }

    /**
     * Scenario: Non-Video Update.
     * Updating metadata without enabling video (e.g., name change)
     * should NOT trigger speaker logic.
     */
    @Test
    fun `updateData with Video FALSE ignores speaker`() {
        val initial = CallMetadata(callId = "test-audio", hasVideo = false)
        val connection = createConnection(initial)
        simulateEndpoints(connection)

        // Update just name, Video remains false.
        val update = CallMetadata(callId = "test-audio", displayName = "New Name")

        connection.updateData(update)

        verify(connection, never()).toggleSpeaker(true)
    }

    /**
     * Scenario: Video Disabled.
     * User turns OFF video during a call.
     * Logic should NOT enforce speaker (usually we do not auto-switch to earpiece, but we definitely don't force speaker).
     */
    @Test
    fun `updateData turning Video OFF does not enforce speaker`() {
        // Start with Video call
        val initial = CallMetadata(callId = "test-off", hasVideo = true)
        val connection = createConnection(initial)
        simulateEndpoints(connection)

        // Reset spy so we don't count the initial setup (if any)
        org.mockito.Mockito.clearInvocations(connection)

        // Update: Turn Video OFF
        val update = CallMetadata(callId = "test-off", hasVideo = false)

        connection.updateData(update)

        verify(connection, never()).toggleSpeaker(true)
    }
}
