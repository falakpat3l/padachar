package com.falakpatel.stridelocal.labs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * LABS: posture alerts while the screen is in use (run it only while the app is visible).
 *
 * 1. Screen-to-face distance: most phones expose the proximity sensor as near/far only
 *    (about 5 cm). If the sensor reports a real range (some ToF sensors do), we use cm.
 * 2. "Text neck": if the phone is held low and flat (pitch under 35 degrees) for over a
 *    minute, the head is likely bent forward. Works on every phone, no camera needed.
 */
class PostureMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val _state = MutableStateFlow(PostureState())
    val state: StateFlow<PostureState> = _state
    private var lowSinceMs = -1L

    fun start() {
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val v = event.values[0]
                val max = event.sensor.maximumRange
                val ranged = max > 10f // binary sensors report only 0 or max (usually 5 cm or less)
                _state.value = _state.value.copy(
                    distanceCm = if (ranged) v else null,
                    faceTooClose = if (ranged) v < PostureRules.MIN_SAFE_CM else v < max,
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val pitch = PostureRules.pitchDegrees(event.values[1], event.values[2])
                val now = System.currentTimeMillis()
                if (pitch < PostureRules.LOW_PITCH_DEG) { if (lowSinceMs < 0) lowSinceMs = now } else lowSinceMs = -1
                _state.value = _state.value.copy(
                    pitchDeg = pitch,
                    neckWarning = lowSinceMs >= 0 && now - lowSinceMs > PostureRules.NECK_HOLD_MS,
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
