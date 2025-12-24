package com.webtrit.callkeep.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [CallMetadata.mergeWith].
 *
 * Verifies that partial updates (patching) work correctly, ensuring that
 * specific fields can be updated without resetting other fields to null/defaults.
 */
class CallMetadataUpdateTest {

    private class FakePhoneConnection(initialMetadata: CallMetadata) {
        var metadata: CallMetadata = initialMetadata
            private set

        fun updateData(requestCallMetadata: CallMetadata) {
            metadata = metadata.mergeWith(requestCallMetadata)
        }
    }

    /**
     * Scenario: Incoming Push Event.
     * We receive a push update containing only the new display name.
     * Existing flags (video, speaker) must be preserved and not reset to null.
     */
    @Test
    fun `mergeWith updates only display name and preserves flags`() {
        val initial = CallMetadata(
            callId = "call-uuid-1",
            hasVideo = true,
            hasSpeaker = false,
            displayName = "Unknown"
        )
        val connection = FakePhoneConnection(initial)

        val update = CallMetadata(
            callId = "call-uuid-1",
            displayName = "John Doe"
        )
        connection.updateData(update)

        val result = connection.metadata
        assertEquals("John Doe", result.displayName)
        assertEquals(true, result.hasVideo)
        assertEquals(false, result.hasSpeaker)
    }

    /**
     * Scenario: Blind Transfer Initiated.
     * The proximity sensor state is updated during a transfer.
     * Critical UI data like Handle and Name must be preserved to avoid UI flickering.
     */
    @Test
    fun `mergeWith updates only proximity and preserves handle and name`() {
        val handle = CallHandle("100")
        val initial = CallMetadata(
            callId = "call-uuid-2",
            displayName = "Alice",
            handle = handle,
            proximityEnabled = false
        )
        val connection = FakePhoneConnection(initial)

        val update = CallMetadata(
            callId = "call-uuid-2",
            proximityEnabled = true
        )
        connection.updateData(update)

        val result = connection.metadata
        assertEquals(true, result.proximityEnabled)
        assertEquals("Alice", result.displayName)
        assertEquals(handle, result.handle)
    }

    /**
     * Scenario: Camera Enabled.
     * The user enables video, sending only `hasVideo = true`.
     * Other states, such as Mute, must remain unchanged.
     */
    @Test
    fun `mergeWith updates only hasVideo and preserves other flags`() {
        val initial = CallMetadata(
            callId = "call-uuid-3",
            hasVideo = false,
            hasMute = true
        )
        val connection = FakePhoneConnection(initial)

        val update = CallMetadata(
            callId = "call-uuid-3",
            hasVideo = true
        )
        connection.updateData(update)

        val result = connection.metadata
        assertEquals(true, result.hasVideo)
        assertEquals(true, result.hasMute)
    }

    /**
     * Scenario: Logic Edge Case (Explicit False).
     * If the update explicitly contains `false`, it must overwrite an existing `true`.
     * It should not be treated as "missing value".
     */
    @Test
    fun `mergeWith overwrites true with explicit false`() {
        val initial = CallMetadata("id", hasVideo = true)
        val connection = FakePhoneConnection(initial)

        val update = CallMetadata("id", hasVideo = false)
        connection.updateData(update)

        assertEquals(false, connection.metadata.hasVideo)
    }

    /**
     * Scenario: Logic Edge Case (Empty List).
     * An empty list in the update object should be treated as "no change"
     * and the existing list should be preserved.
     */
    @Test
    fun `mergeWith preserves audio devices when update contains empty list`() {
        val device = AudioDevice(AudioDeviceType.EARPIECE, "Ear", "1")
        val initial = CallMetadata("id", audioDevices = listOf(device))
        val connection = FakePhoneConnection(initial)

        val update = CallMetadata("id", audioDevices = emptyList())
        connection.updateData(update)

        assertEquals(1, connection.metadata.audioDevices.size)
        assertEquals(device, connection.metadata.audioDevices[0])
    }

    /**
     * Scenario: Logic Edge Case (New List).
     * A non-empty list in the update object should completely replace
     * the existing list.
     */
    @Test
    fun `mergeWith replaces audio devices when update contains new list`() {
        val device1 = AudioDevice(AudioDeviceType.EARPIECE, "Ear", "1")
        val initial = CallMetadata("id", audioDevices = listOf(device1))
        val connection = FakePhoneConnection(initial)

        val device2 = AudioDevice(AudioDeviceType.SPEAKER, "Spk", "2")
        val update = CallMetadata("id", audioDevices = listOf(device2))
        connection.updateData(update)

        assertEquals(1, connection.metadata.audioDevices.size)
        assertEquals(device2, connection.metadata.audioDevices[0])
    }
}
