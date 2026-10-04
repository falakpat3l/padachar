package com.falakpatel.stridelocal.labs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Accelerometer driver for [RespirationEstimator]. Phone flat on the chest, user lying still. */
class RespirationMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val estimator = RespirationEstimator()
    private val _rate = MutableStateFlow<Int?>(null)
    val breathsPerMinute: StateFlow<Int?> = _rate

    // Average 5 raw samples (50 Hz) into one 10 Hz sample: cheap anti-alias filter.
    private val acc = DoubleArray(3)
    private var count = 0

    fun start(): Boolean {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        return sensorManager.registerListener(this, accel, 20_000)
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        for (i in 0..2) acc[i] += event.values[i]
        if (++count == 5) {
            estimator.add(acc[0] / 5, acc[1] / 5, acc[2] / 5)
            acc.fill(0.0); count = 0
            _rate.value = estimator.breathsPerMinute()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
