package com.webtrit.callkeep.managers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.setLoopingCompat

class AudioManager(
    val context: Context,
) {
    private val audioManager =
        requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    private var ringtone: Ringtone? = null
    private var ringBack: MediaPlayer? = null
    private var callWaitingToneGenerator: ToneGenerator? = null
    private val callWaitingHandler = Handler(Looper.getMainLooper())
    private val callWaitingRunnable =
        object : Runnable {
            override fun run() {
                callWaitingToneGenerator?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 1000)
                callWaitingHandler.postDelayed(this, 3000)
            }
        }

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
     * Check if a Bluetooth headset is connected via SCO (active call audio profile).
     *
     * @return True if a Bluetooth SCO headset is connected, false otherwise.
     */
    fun isBluetoothConnected(): Boolean = isInputDeviceConnected(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

    /**
     * Check if a Bluetooth device is available via A2DP (media profile).
     *
     * BT SCO is not established until the call becomes active; A2DP is connected earlier and
     * indicates a BT headset is present before SCO comes up.
     */
    fun isBluetoothA2dpAvailable(): Boolean = isOutputDeviceConnected(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

    /**
     * Check if the speakerphone is currently on.
     *
     * @return True if the speakerphone is on, false otherwise.
     */
    fun isSpeakerphoneOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            audioManager.isSpeakerphoneOn
        }

    /**
     * Start playing the ringtone, or vibrate if the device is in vibrate-only mode.
     *
     * The Ringtone API plays through the ringtone audio stream, which is muted in
     * RINGER_MODE_VIBRATE. In that case we skip the ringtone and start a repeating
     * vibration pattern directly so the user is notified of the incoming call.
     *
     * Some OEM ROMs (e.g. MIUI/HyperOS on Xiaomi) report vibrate mode as
     * RINGER_MODE_NORMAL with STREAM_RING volume = 0 instead of RINGER_MODE_VIBRATE.
     * In that case Ringtone.play() runs silently and no vibration is triggered.
     * We detect this by checking stream volume and treat it as vibrate mode.
     */
    fun startRingtone(ringtoneSound: String?) {
        ringtone?.stop()
        val ringVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING)
        Log.i(TAG, "startRingtone: ringerMode=${audioManager.ringerMode}, ringVolume=$ringVolume, vibratorAvailable=${vibrator != null}")
        when (audioManager.ringerMode) {
            android.media.AudioManager.RINGER_MODE_VIBRATE -> {
                ringtone = null
                startVibration()
            }

            android.media.AudioManager.RINGER_MODE_NORMAL -> {
                if (ringVolume == 0) {
                    // OEM quirk: vibrate mode reported as NORMAL with zero ring volume
                    ringtone = null
                    startVibration()
                } else {
                    val pendingRingtone = ringtoneSound?.let { getRingtone(it) } ?: getDefaultRingtone()
                    ringtone = pendingRingtone
                    ringtone?.setLoopingCompat(true)
                    ringtone?.play()
                }
            }

            else -> {
                Log.d(TAG, "startRingtone: ringer mode is silent, skipping audio and vibration")
            }
        }
    }

    private fun startVibration() {
        if (vibrator == null) {
            Log.w(TAG, "startVibration: vibrator is null, skipping")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val attrs =
                android.os.VibrationAttributes
                    .Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_RINGTONE)
                    .build()
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_AMPLITUDES, 0), attrs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs =
                AudioAttributes
                    .Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_AMPLITUDES, 0), attrs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_PATTERN, 0)
        }
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
     * Stop playing the ringtone and cancel any active vibration.
     */
    fun stopRingtone() {
        ringtone?.stop()
        vibrator?.cancel()
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

    /**
     * Play a soft call-waiting beep through the voice call audio stream.
     *
     * Uses STREAM_VOICE_CALL so the tone respects in-call volume and routes through
     * the earpiece/headset - not the ringtone stream, which would blast at full
     * ringtone volume while the user has the phone to their ear.
     *
     * Repeats every 3 seconds until [stopCallWaitingTone] is called.
     */
    fun startCallWaitingTone() {
        stopCallWaitingTone()
        callWaitingToneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME / 2)
        callWaitingRunnable.run()
    }

    /**
     * Stop the call-waiting beep and release the tone generator.
     */
    fun stopCallWaitingTone() {
        callWaitingHandler.removeCallbacks(callWaitingRunnable)
        callWaitingToneGenerator?.stopTone()
        callWaitingToneGenerator?.release()
        callWaitingToneGenerator = null
    }

    companion object {
        private const val TAG = "AudioManager"

        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 1000)
        private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0)
    }
}
