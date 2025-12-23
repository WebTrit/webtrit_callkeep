package com.webtrit.callkeep.services.services.connection

import android.content.Context
import com.webtrit.callkeep.common.Log

class ProximitySensorManager(
    private val context: Context, private val state: PhoneConnectionConsts
) {
    private val sensorListener = PhoneSensorListener()

    init {
        logger.d("Initializing ProximitySensorManager")
        sensorListener.setSensorHandler { isUserNear ->
            handleSensorChange(isUserNear)
        }
    }

    /**
     * Updates the proximity listening preference.
     */
    fun setShouldListenProximity(shouldListen: Boolean) {
        logger.d("Setting shouldListenProximity: $shouldListen")
        state.setShouldListenProximity(shouldListen)
    }

    /**
     * Updates the proximity wake lock based on the current state and sensor readings.
     */
    fun updateProximityWakelock() {
        val isNear = state.isUserNear()
        val shouldListen = state.shouldListenProximity()
        val active = shouldListen && isNear

        logger.v("Updating proximity wakelock. State: [isNear: $isNear, shouldListen: $shouldListen] -> Active: $active")
        sensorListener.upsertProximityWakelock(active)
    }

    /**
     * Starts listening to proximity sensor changes.
     */
    fun startListening() {
        logger.i("Starting proximity sensor listening")
        sensorListener.listen(context)
    }

    /**
     * Stops listening to proximity sensor changes.
     */
    fun stopListening() {
        logger.i("Stopping proximity sensor listening")
        sensorListener.unListen(context)
    }

    /**
     * Internal handler for sensor state changes to keep the callback concise.
     */
    private fun handleSensorChange(isUserNear: Boolean) {
        logger.v("Proximity sensor change detected. Is user near: $isUserNear")
        state.setNearestState(isUserNear)
        updateProximityWakelock()
    }

    companion object {
        private const val TAG = "ProximitySensorManager"
        private val logger = Log(TAG)
    }
}
