package com.webtrit.callkeep.services

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

class AudioService(context: Context) {
    private val audioManager = requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

    private fun isInputDeviceConnected(type: Int): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { it.type == type }
    }

    /**
     * Set the microphone mute state.
     *
     * @param isMicrophoneMute True to mute the microphone, false to unmute it.
     */
    fun setMicrophoneMute(isMicrophoneMute: Boolean) {
        audioManager.isMicrophoneMute = isMicrophoneMute
    }

    /**
     * Check if a wired headset is connected.
     *
     * @return True if a wired headset is connected, false otherwise.
     */
    fun isWiredHeadsetConnected(): Boolean {
        return isInputDeviceConnected(AudioDeviceInfo.TYPE_WIRED_HEADSET)
    }

    /**
     * Check if a Bluetooth headset is connected.
     *
     * @return True if a Bluetooth headset is connected, false otherwise.
     */
    fun isBluetoothConnected(): Boolean {
        return isInputDeviceConnected(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    }
}
