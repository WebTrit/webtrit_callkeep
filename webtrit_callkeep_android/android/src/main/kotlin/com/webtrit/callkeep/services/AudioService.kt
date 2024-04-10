package com.webtrit.callkeep.services

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import io.flutter.FlutterInjector
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class AudioService(val context: Context) {
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
        
        if (ringtoneSound != null) {
            val loader = FlutterInjector.instance().flutterLoader()
            val key = loader.getLookupKeyForAsset(ringtoneSound)

            // Load the ringtone from the assets to cache
            // TODO: find a solution to play the ringtone directly from the assets
            val fd = context.assets.open(key)
            val cacheFile = File(context.cacheDir, "ringtone.mp3")
            val outputStream = FileOutputStream(cacheFile)
            val buf = ByteArray(1024)
            var len: Int
            while (fd.read(buf).also { len = it } > 0) {
                outputStream.write(buf, 0, len)
            }
            fd.close()
            outputStream.close()

            ringtone = RingtoneManager.getRingtone(context, Uri.fromFile(cacheFile))
        } else {
            ringtone =
                RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true

        ringtone?.play()
    }

    /**
     * Stop playing the ringtone.
     */
    fun stopRingtone() {
        ringtone?.stop()
    }

}
