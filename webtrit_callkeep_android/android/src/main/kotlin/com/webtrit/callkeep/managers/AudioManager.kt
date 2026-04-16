package com.webtrit.callkeep.managers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.setLoopingCompat

class AudioManager(
    val context: Context,
) {
    private val audioManager =
        requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private var ringtone: Ringtone? = null
    private var ringBack: MediaPlayer? = null

    private fun isInputDeviceConnected(type: Int): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { it.type == type }
    }

    private fun isOutputDeviceConnected(type: Int): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == type }
    }

    /**
     * Check if the device supports earpiece.
     *
     * @return True if the device supports earpiece, false otherwise.
     */
    fun isSupportEarpiese(): Boolean = isOutputDeviceConnected(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
    
    /**
     * Check if the device supports speakerphone.
     *
     * @return True if the device supports speakerphone, false otherwise.
     */
    fun isSupportSpeakerphone(): Boolean = isOutputDeviceConnected(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)


    /**
     * Check if a wired headset is connected.
     *
     * @return True if a wired headset is connected, false otherwise.
     */
    fun isWiredHeadsetConnected(): Boolean = isInputDeviceConnected(AudioDeviceInfo.TYPE_WIRED_HEADSET)

    /**
     * Check if a Bluetooth headset is connected.
     *
     * @return True if a Bluetooth headset is connected, false otherwise.
     */
    fun isBluetoothConnected(): Boolean = isInputDeviceConnected(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)


    /**
     * Check if the speakerphone is currently on.
     *
     * @return True if the speakerphone is on, false otherwise.
     */
    fun isSpeakerphoneOn(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    } else {
        audioManager.isSpeakerphoneOn
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

    private fun getDefaultRingtone(): Ringtone =
        RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )

    private fun getRingtone(asset: String): Ringtone =
        try {
            val path = AssetCacheManager.getAsset(asset)
            Log.i("AudioService", "Used asset: $path")
            RingtoneManager.getRingtone(context, path)
        } catch (e: Exception) {
            Log.e("AudioService", "$e")
            getDefaultRingtone()
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
        val path = AssetCacheManager.getAsset(asset)
        val attributes =
            AudioAttributes
                .Builder()
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
        try {
            ringBack?.release()
        } finally {
            ringBack = null
        }
    }
}
