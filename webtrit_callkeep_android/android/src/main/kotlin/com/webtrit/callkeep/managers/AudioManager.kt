package com.webtrit.callkeep.managers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager

import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.helpers.setLoopingCompat

class AudioManager(val context: Context) {
    private val audioManager = requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private var ringtone: Ringtone? = null
    private var ringBack: MediaPlayer? = null

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

    /**
     * Create a MediaPlayer instance for the ringback sound.
     *
     * used to play the ringback sound when the call is in the dialing state. eg SIP 180 Ringing.
     * important to use USAGE_VOICE_COMMUNICATION_SIGNALLING to ensure the ringback sound cant conflict with webrtc audio.
     * if use regular `media` usage it will be ducked by webrtc audio.
     * if use `ringtone` usage it will be controlled by the ringtone volume
     * and silent mode that is absolutely wrong. Also on android 9+ it will muted most of the time.
     *
     * @param asset The flutters ringback sound asset.
     */
    private fun createRingback(asset: String): MediaPlayer {
        val path = AssetHolder.flutterAssetManager.getAsset(asset)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            .build()
        val session = audioManager.generateAudioSessionId()
        return MediaPlayer.create(context, path, null, attributes, session).apply {
            isLooping = true
        }
    }

    /**
     * Start playing the ringback sound.
     *
     * @param asset The flutters ringback sound asset.
     */
    fun startRingback(asset: String) {
        if (ringBack == null) ringBack = createRingback(asset)
        ringBack?.start()
    }

    /**
     * Stop playing the ringback sound.
     */
    fun stopRingback() {
        ringBack?.pause()
    }

}
