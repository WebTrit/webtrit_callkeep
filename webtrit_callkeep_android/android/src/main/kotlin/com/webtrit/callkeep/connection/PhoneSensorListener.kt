package com.webtrit.callkeep.connection

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.telecom.ConnectionService
import android.util.Log

import com.webtrit.callkeep.common.ValueCallback

class PhoneSensorListener : SensorEventListener {
    private var proximityWakelock: PowerManager.WakeLock? = null
    private var sensorHandler: ValueCallback<Boolean>? = null

    fun setSensorHandler(callback: ValueCallback<Boolean>?) {
        this.sensorHandler = callback
    }

    @SuppressLint("InvalidWakeLockTag")
    fun listen(context: Context) {
        val sensorManager =
            context.getSystemService(ConnectionService.SENSOR_SERVICE) as SensorManager
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        try {
            if (proximity != null) {
                val manager =
                    context.getSystemService(ConnectionService.POWER_SERVICE) as PowerManager
                proximityWakelock = manager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callkeep-voip"
                )
                sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (x: java.lang.Exception) {
            Log.e(LOG_TAG, x.toString())
        }
    }

    fun unListen(context: Context) {
        val sensorManager =
            context.getSystemService(ConnectionService.SENSOR_SERVICE) as SensorManager
        try {
            sensorManager.unregisterListener(this)
        } catch (x: java.lang.Exception) {
            Log.e(LOG_TAG, x.toString())
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_PROXIMITY) {
                val isNearby = it.values[0] < it.sensor.maximumRange.coerceAtMost(3f)
                sensorHandler?.invoke(isNearby)
            }
        }
    }

    fun turnOff() {
        try {
            proximityWakelock?.acquire()
        } catch (x: Exception) {
            Log.e(LOG_TAG, x.toString())
        }
    }

    fun turnOn() {
        try {
            if (proximityWakelock?.isHeld == true) proximityWakelock?.release(1)
        } catch (x: Exception) {
            Log.e(LOG_TAG, x.toString())
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) = Unit

    companion object {
        private const val LOG_TAG = "PhoneSensorListener"
    }
}
