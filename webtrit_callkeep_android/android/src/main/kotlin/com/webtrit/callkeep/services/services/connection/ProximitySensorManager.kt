package com.webtrit.callkeep.services.services.connection

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.telecom.ConnectionService
import com.webtrit.callkeep.common.Log

class ProximitySensorManager(
    private val context: Context,
    private val state: PhoneConnectionConsts,
) {
    private val sensorListener = PhoneSensorListener()

    @Volatile
    private var isListening = false

    @Volatile
    private var isWakelockActive = false

    init {
        logger.d("Initializing ProximitySensorManager")
    }

    fun setShouldListenProximity(shouldListen: Boolean) {
        if (state.shouldListenProximity() == shouldListen) return

        logger.d("Setting shouldListenProximity: $shouldListen")
        state.setShouldListenProximity(shouldListen)
        updateProximityWakelock()
    }

    fun updateProximityWakelock() {
        val active = isListening && state.shouldListenProximity()

        if (isWakelockActive == active) return
        isWakelockActive = active

        logger.v(
            "Updating proximity wakelock. State: [shouldListen: ${state.shouldListenProximity()}, isListening: $isListening] -> Active: $active",
        )
        sensorListener.upsertProximityWakelock(context, active)
    }

    fun startListening() {
        if (isListening) return

        logger.i("Starting proximity sensor listening")
        isListening = true
        updateProximityWakelock()
    }

    fun stopListening() {
        if (!isListening) return

        logger.i("Stopping proximity sensor listening")
        isListening = false
        updateProximityWakelock()
    }

    companion object {
        private const val TAG = "ProximitySensorManager"
        private val logger = Log(TAG)
    }
}

class PhoneSensorListener {
    private var proximityWakelock: PowerManager.WakeLock? = null

    @Synchronized
    @SuppressLint("InvalidWakeLockTag")
    fun upsertProximityWakelock(
        context: Context,
        turnOn: Boolean,
    ) {
        try {
            if (proximityWakelock == null) {
                val manager =
                    context.getSystemService(ConnectionService.POWER_SERVICE) as PowerManager
                proximityWakelock =
                    manager.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callkeep-voip",
                    ).apply {
                        setReferenceCounted(false)
                    }
            }

            val wakelock = proximityWakelock ?: return
            val alreadyHeld = wakelock.isHeld

            if (turnOn && !alreadyHeld) {
                wakelock.acquire(60 * 60 * 1000L)
            } else if (!turnOn && alreadyHeld) {
                wakelock.release(1)
            }
        } catch (x: Exception) {
            Log.e(LOG_TAG, x.toString())
        }
    }

    companion object {
        private const val LOG_TAG = "PhoneSensorListener"
    }
}
