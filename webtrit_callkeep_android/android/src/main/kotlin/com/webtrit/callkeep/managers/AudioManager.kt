package com.webtrit.callkeep.managers

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager

import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.helpers.setLoopingCompat

class AudioManager(val context: Context) {
    private val audioManager = requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private var ringtone: Ringtone? = null

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

    /**
     * Start playing the ringtone.
     */
    fun startRingtone(ringtoneSound: String?) {
        ringtone?.stop()
        ringtone = ringtoneSound?.let { getRingtone(it) } ?: getDefaultRingtone()
        ringtone?.setLoopingCompat(true)
        ringtone?.play()
    }

    private fun getDefaultRingtone(): Ringtone {
        return RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
    }

    private fun getRingtone(asset: String): Ringtone {
        return try {
            val path = AssetHolder.flutterAssetManager.getAsset(asset)

            if (path != null) {
                Log.i("AudioService", "Used asset: $path")
                return RingtoneManager.getRingtone(context, path)
            } else {
                Log.i("AudioService", "Used system ringtone")
                getDefaultRingtone()
            }
        } catch (e: Exception) {
            Log.e("AudioService", "$e")
            getDefaultRingtone()
        }
    }

    /**
     * Stop playing the ringtone.
     */
    fun stopRingtone() {
        ringtone?.stop()
    }
}
