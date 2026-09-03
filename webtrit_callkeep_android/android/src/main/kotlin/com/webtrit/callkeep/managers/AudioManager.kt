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
import android.provider.Settings
import com.webtrit.callkeep.common.AssetCacheManager
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.setLoopingCompat
import com.webtrit.callkeep.models.AudioDevice
import com.webtrit.callkeep.models.AudioDeviceType

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
                    ringtone = ringtoneSound?.let { getRingtone(it) } ?: getDefaultRingtone()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_AMPLITUDES, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs =
                    android.os.VibrationAttributes
                        .Builder()
                        .setUsage(android.os.VibrationAttributes.USAGE_RINGTONE)
                        .build()
                vibrator.vibrate(effect, attrs)
            } else {
                // On some MIUI/HyperOS builds, vibrate() with USAGE_NOTIFICATION_RINGTONE is silently
                // suppressed because VibratorService.shouldVibrateForRingtone() reads a proprietary
                // settings key instead of the standard VIBRATE_WHEN_RINGING (field observation,
                // not confirmed from official MIUI source code).
                // Field report (Xiaomi.eu forums, MIUI v10+): "MIUI overrides VibratorService with a
                // proprietary implementation that reads an internal settings key instead of the standard
                // VIBRATE_WHEN_RINGING. For third-party apps the standard key always resolves to 0,
                // so shouldVibrateForRingtone() returns false even when the device is in vibrate mode."
                // When VIBRATE_WHEN_RINGING is 0, fall back to vibrate() without AudioAttributes so
                // shouldVibrateForRingtone() is bypassed entirely (USAGE_UNKNOWN -> isRingtone() == false).
                @Suppress("DEPRECATION")
                val vibrateWhenRinging =
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_WHEN_RINGING,
                        1,
                    )
                Log.d(TAG, "startVibration: sdk=${Build.VERSION.SDK_INT}, vibrateWhenRinging=$vibrateWhenRinging")
                if (vibrateWhenRinging == 0) {
                    vibrator.vibrate(effect)
                    Log.d(TAG, "startVibration: used no-attrs fallback (vibrateWhenRinging=0)")
                } else {
                    val attrs =
                        AudioAttributes
                            .Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    vibrator.vibrate(effect, attrs)
                }
            }
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

    /**
     * The audio devices currently usable for a call, in the order the UI presents them.
     *
     * Queried from the platform on every call, so a headset that was already connected when
     * the call started is included. A device connected mid-call is not picked up until this
     * is queried again.
     */
    fun availableDevices(): List<AudioDevice> =
        buildAvailableDevices(
            hasEarpiece = isSupportEarpiese(),
            hasSpeaker = isSupportSpeakerphone(),
            hasWiredHeadset = isWiredHeadsetConnected(),
            hasBluetooth = isBluetoothConnected(),
        )

    /**
     * The device audio is currently going to, derived from what is connected and from the
     * speakerphone flag.
     */
    fun currentDevice(): AudioDevice =
        selectCurrentDevice(
            hasBluetooth = isBluetoothConnected(),
            hasWiredHeadset = isWiredHeadsetConnected(),
            isSpeakerOn = isSpeakerphoneOn(),
        )

    /**
     * Routes call audio to [type] without a Telecom framework underneath.
     *
     * This is NOT the same as the routing used on the Telecom path
     * ([com.webtrit.callkeep.services.services.connection.PhoneConnection]), and the difference
     * is deliberate: there, Telecom owns the Bluetooth link and starting SCO here as well would
     * fight it. On the standalone path nothing owns it, so below API 31 - where
     * [AudioManager.setCommunicationDevice] does not exist - the SCO link has to be started and
     * stopped explicitly, or selecting a headset silently does nothing.
     */
    fun routeTo(type: AudioDeviceType) {
        val mode = audioManager.mode
        Log.d(TAG, "routeTo: type=$type, audioMode=$mode")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mode == AudioManager.MODE_IN_COMMUNICATION) {
            val targetType = communicationDeviceType(type)
            if (targetType == null) {
                Log.w(TAG, "routeTo: unsupported type=$type, skipping")
                return
            }
            val deviceInfo =
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == targetType }
            if (deviceInfo != null && audioManager.setCommunicationDevice(deviceInfo)) {
                Log.d(TAG, "routeTo: setCommunicationDevice succeeded for type=$type")
                return
            }
            Log.w(TAG, "routeTo: setCommunicationDevice failed for type=$type, falling back")
        }

        routeLegacy(type)
    }

    /**
     * Pre-API-31 routing: the Bluetooth link is a separate connection that has to be opened and
     * closed by hand, and everything else is the speakerphone flag.
     */
    @Suppress("DEPRECATION")
    private fun routeLegacy(type: AudioDeviceType) {
        if (type == AudioDeviceType.BLUETOOTH) {
            audioManager.isSpeakerphoneOn = false
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.d(TAG, "routeLegacy: bluetooth SCO started")
            return
        }
        if (audioManager.isBluetoothScoOn) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            Log.d(TAG, "routeLegacy: bluetooth SCO stopped")
        }
        audioManager.isSpeakerphoneOn = (type == AudioDeviceType.SPEAKER)
        Log.d(TAG, "routeLegacy: setSpeakerphoneOn=${type == AudioDeviceType.SPEAKER}")
    }

    /**
     * Gives the audio route back when the call ends.
     *
     * The Bluetooth link matters here: [routeLegacy] opens it by hand below API 31, and nothing
     * else closes it, so without this a call taken on a headset leaves the link open afterwards.
     */
    fun releaseRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        if (audioManager.isBluetoothScoOn) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        Log.d(TAG, "releaseRoute: audio route released")
    }

    private fun communicationDeviceType(type: AudioDeviceType): Int? =
        when (type) {
            AudioDeviceType.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            AudioDeviceType.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            AudioDeviceType.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            AudioDeviceType.WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
            else -> null
        }

    companion object {
        private const val TAG = "AudioManager"

        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 1000)
        private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0)

        /**
         * Builds the device list from what is connected. Order is what the UI shows, so the two
         * built-in outputs come first and the headsets after them.
         */
        fun buildAvailableDevices(
            hasEarpiece: Boolean,
            hasSpeaker: Boolean,
            hasWiredHeadset: Boolean,
            hasBluetooth: Boolean,
        ): List<AudioDevice> =
            buildList {
                if (hasEarpiece) add(AudioDevice(AudioDeviceType.EARPIECE))
                if (hasSpeaker) add(AudioDevice(AudioDeviceType.SPEAKER))
                if (hasWiredHeadset) add(AudioDevice(AudioDeviceType.WIRED_HEADSET))
                if (hasBluetooth) add(AudioDevice(AudioDeviceType.BLUETOOTH))
            }

        /**
         * Picks the device audio is going to. A connected headset wins over the built-in outputs,
         * and Bluetooth wins over a wired one, which is the order the platform itself prefers.
         */
        fun selectCurrentDevice(
            hasBluetooth: Boolean,
            hasWiredHeadset: Boolean,
            isSpeakerOn: Boolean,
        ): AudioDevice =
            when {
                hasBluetooth -> AudioDevice(AudioDeviceType.BLUETOOTH)
                hasWiredHeadset -> AudioDevice(AudioDeviceType.WIRED_HEADSET)
                isSpeakerOn -> AudioDevice(AudioDeviceType.SPEAKER)
                else -> AudioDevice(AudioDeviceType.EARPIECE)
            }
    }
}
