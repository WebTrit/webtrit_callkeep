package com.webtrit.callkeep.services.connection

import android.content.Context

class ProximitySensorManager(
    private val context: Context,
    private val state: PhoneConnectionConsts
) {
    private val sensorListener = PhoneSensorListener()

    init {
        sensorListener.setSensorHandler { isUserNear ->
            state.setNearestState(isUserNear)
            updateProximityWakelock()
        }
    }

    fun setShouldListenProximity(shouldListen: Boolean) {
        state.setShouldListenProximity(shouldListen)
    }

    /**
     * Updates the proximity wake lock based on the current state and sensor readings.
     */
    fun updateProximityWakelock() {
        val isNear = state.isUserNear()
        val shouldListen = state.shouldListenProximity()
        sensorListener.upsertProximityWakelock(shouldListen && isNear)
    }

    /**
     * Starts listening to proximity sensor changes.
     */
    fun startListening() {
        sensorListener.listen(context)
    }

    /**
     * Stops listening to proximity sensor changes.
     */
    fun stopListening() {
        sensorListener.unListen(context)
    }
}
