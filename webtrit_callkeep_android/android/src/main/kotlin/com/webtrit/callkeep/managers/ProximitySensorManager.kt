package com.webtrit.callkeep.managers

import android.content.Context
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionConsts
import com.webtrit.callkeep.services.telecom.connection.PhoneSensorListener

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
