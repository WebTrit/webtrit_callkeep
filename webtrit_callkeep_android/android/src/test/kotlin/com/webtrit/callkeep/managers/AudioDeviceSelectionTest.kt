package com.webtrit.callkeep.managers

import com.webtrit.callkeep.models.AudioDeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the two pure decisions behind the audio device picker:
 * [AudioManager.buildAvailableDevices] (what is offered) and
 * [AudioManager.selectCurrentDevice] (what is in use).
 *
 * Both are shared by the Telecom path and the standalone one, which is the point: before this,
 * standalone offered a fixed earpiece/speaker pair and no headset could be chosen at all.
 */
class AudioDeviceSelectionTest {
    @Test
    fun `offers a connected bluetooth headset alongside the built-in outputs`() {
        val devices =
            AudioManager.buildAvailableDevices(
                hasEarpiece = true,
                hasSpeaker = true,
                hasWiredHeadset = false,
                hasBluetooth = true,
            )
        assertEquals(
            listOf(AudioDeviceType.EARPIECE, AudioDeviceType.SPEAKER, AudioDeviceType.BLUETOOTH),
            devices.map { it.type },
        )
    }

    @Test
    fun `offers a connected wired headset alongside the built-in outputs`() {
        val devices =
            AudioManager.buildAvailableDevices(
                hasEarpiece = true,
                hasSpeaker = true,
                hasWiredHeadset = true,
                hasBluetooth = false,
            )
        assertEquals(
            listOf(AudioDeviceType.EARPIECE, AudioDeviceType.SPEAKER, AudioDeviceType.WIRED_HEADSET),
            devices.map { it.type },
        )
    }

    @Test
    fun `offers both headsets when both are connected, built-in outputs first`() {
        val devices =
            AudioManager.buildAvailableDevices(
                hasEarpiece = true,
                hasSpeaker = true,
                hasWiredHeadset = true,
                hasBluetooth = true,
            )
        assertEquals(
            listOf(
                AudioDeviceType.EARPIECE,
                AudioDeviceType.SPEAKER,
                AudioDeviceType.WIRED_HEADSET,
                AudioDeviceType.BLUETOOTH,
            ),
            devices.map { it.type },
        )
    }

    @Test
    fun `omits an output the device does not have`() {
        // A tablet with no earpiece: speaker only, and nothing invented to fill the gap.
        val devices =
            AudioManager.buildAvailableDevices(
                hasEarpiece = false,
                hasSpeaker = true,
                hasWiredHeadset = false,
                hasBluetooth = false,
            )
        assertEquals(listOf(AudioDeviceType.SPEAKER), devices.map { it.type })
    }

    @Test
    fun `bluetooth wins over a wired headset as the device in use`() {
        val device =
            AudioManager.selectCurrentDevice(
                hasBluetooth = true,
                hasWiredHeadset = true,
                isSpeakerOn = false,
            )
        assertEquals(AudioDeviceType.BLUETOOTH, device.type)
    }

    @Test
    fun `a wired headset wins over the speakerphone flag`() {
        // The flag can still be on from earlier in the call; a headset in the socket outranks it.
        val device =
            AudioManager.selectCurrentDevice(
                hasBluetooth = false,
                hasWiredHeadset = true,
                isSpeakerOn = true,
            )
        assertEquals(AudioDeviceType.WIRED_HEADSET, device.type)
    }

    @Test
    fun `falls back to the speaker when it is on and no headset is connected`() {
        val device =
            AudioManager.selectCurrentDevice(
                hasBluetooth = false,
                hasWiredHeadset = false,
                isSpeakerOn = true,
            )
        assertEquals(AudioDeviceType.SPEAKER, device.type)
    }

    @Test
    fun `falls back to the earpiece when nothing else applies`() {
        val device =
            AudioManager.selectCurrentDevice(
                hasBluetooth = false,
                hasWiredHeadset = false,
                isSpeakerOn = false,
            )
        assertEquals(AudioDeviceType.EARPIECE, device.type)
    }
}
