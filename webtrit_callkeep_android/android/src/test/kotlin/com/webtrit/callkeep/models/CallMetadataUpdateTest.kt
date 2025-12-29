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

        val update = CallMetadata(
            callId = "call-uuid-1",
            displayName = "John Doe"
        )

        val result = initial.mergeWith(update)

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

        val update = CallMetadata(
            callId = "call-uuid-2",
            proximityEnabled = true
        )

        val result = initial.mergeWith(update)

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

        val update = CallMetadata(
            callId = "call-uuid-3",
            hasVideo = true
        )

        val result = initial.mergeWith(update)

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
        val update = CallMetadata("id", hasVideo = false)

        val result = initial.mergeWith(update)

        assertEquals(false, result.hasVideo)
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

        val update = CallMetadata("id", audioDevices = emptyList())

        val result = initial.mergeWith(update)

        assertEquals(1, result.audioDevices.size)
        assertEquals(device, result.audioDevices[0])
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

        val device2 = AudioDevice(AudioDeviceType.SPEAKER, "Spk", "2")
        val update = CallMetadata("id", audioDevices = listOf(device2))

        val result = initial.mergeWith(update)

        assertEquals(1, result.audioDevices.size)
        assertEquals(device2, result.audioDevices[0])
    }

    /**
     * Scenario: Default Configuration Preservation.
     * The `speakerOnVideo` defaults to NULL (indicating "use system default").
     * Receiving an update for another field (e.g., displayName) must preserve this NULL state.
     */
    @Test
    fun `mergeWith preserves default speakerOnVideo value when updating other fields`() {
        val initial = CallMetadata(callId = "config-test-1") // speakerOnVideo is null
        val update = CallMetadata(callId = "config-test-1", displayName = "Jane")

        val result = initial.mergeWith(update)

        assertEquals("Jane", result.displayName)
        // Should remain null (no override)
        assertEquals(null, result.speakerOnVideo)
    }

    /**
     * Scenario: Custom Configuration Preservation.
     * If `speakerOnVideo` was explicitly set to FALSE, a partial update
     * (which contains a null `speakerOnVideo`) must NOT overwrite the explicit setting.
     */
    @Test
    fun `mergeWith preserves explicit false for speakerOnVideo`() {
        val initial = CallMetadata(
            callId = "config-test-2",
            speakerOnVideo = false
        )
        val update = CallMetadata(
            callId = "config-test-2",
            hasMute = true
        )

        val result = initial.mergeWith(update)

        assertEquals(true, result.hasMute)
        // Must remain false (NOT overwritten by default null)
        assertEquals(false, result.speakerOnVideo)
    }

    /**
     * Scenario: Explicit Update (Disable).
     * Verifies that `speakerOnVideo` can be updated from the default state (null)
     * to explicitly disabled (false).
     */
    @Test
    fun `mergeWith updates speakerOnVideo from null to false`() {
        val initial = CallMetadata(callId = "config-test-3")
        val update = CallMetadata(
            callId = "config-test-3",
            speakerOnVideo = false
        )

        val result = initial.mergeWith(update)

        assertEquals(false, result.speakerOnVideo)
    }

    /**
     * Scenario: Explicit Update (Re-enable).
     * Verifies that `speakerOnVideo` can be updated from explicitly disabled (false)
     * back to explicitly enabled (true).
     */
    @Test
    fun `mergeWith updates speakerOnVideo from false to true`() {
        val initial = CallMetadata(
            callId = "config-test-4",
            speakerOnVideo = false
        )
        val update = CallMetadata(
            callId = "config-test-4",
            speakerOnVideo = true
        )

        val result = initial.mergeWith(update)

        assertEquals(true, result.speakerOnVideo)
    }
}
