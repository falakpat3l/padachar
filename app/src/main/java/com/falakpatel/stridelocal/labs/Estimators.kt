package com.falakpatel.stridelocal.labs

import kotlin.math.atan2
import kotlin.math.sqrt

// Pure signal estimators for the Labs modules (no Android imports, unit tested on the JVM).

/**
 * LABS: camera photoplethysmography (PPG). Not a medical device.
 *
 * The user covers the rear camera and flash with a fingertip. The torch shines through the
 * skin; each heartbeat pushes more blood into the capillaries, which absorbs more light and
 * makes the frame slightly darker. We average frame brightness (~30 fps) and find the period.
 * Frames are processed in memory and dropped immediately. Nothing is saved.
 */
class PpgEstimator(private val windowSec: Double = 12.0) {
    private val times = ArrayDeque<Double>()
    private val values = ArrayDeque<Double>()

    fun add(timeSec: Double, brightness: Double) {
        times.addLast(timeSec); values.addLast(brightness)
        while (times.isNotEmpty() && timeSec - times.first() > windowSec) { times.removeFirst(); values.removeFirst() }
    }

    /** @return beats per minute, or null if the signal is not clean enough yet. */
    fun bpm(): Int? {
        if (times.size < 60) return null
        val duration = times.last() - times.first()
        if (duration < windowSec * 0.6) return null
        val fs = (times.size - 1) / duration
        val x = values.toDoubleArray()
        // Pass about 0.7 to 4 Hz (40 to 240 bpm): remove slow drift and frame noise.
        val filtered = SignalMath.bandPass(x, shortWin = maxOf(1, (fs / 8).toInt()), longWin = maxOf(2, (fs * 1.2).toInt()))
        val minLag = (fs * 60 / 200).toInt().coerceAtLeast(1) // 200 bpm
        val maxLag = (fs * 60 / 40).toInt()                    // 40 bpm
        val (lag, corr) = SignalMath.dominantLag(filtered, minLag, maxLag) ?: return null
        if (corr < 0.3) return null // finger moved or not covering the lens
        return (60.0 * fs / lag).toInt()
    }

    fun reset() { times.clear(); values.clear() }
}

/**
 * LABS: resting breathing rate from the accelerometer. Not a medical device.
 *
 * Note: true seismocardiography (SCG) measures heart vibrations. This module uses the same
 * idea, a phone resting on the chest while lying down, but tracks the much slower chest
 * rise and fall (accelerometer-derived respiration). Each breath tilts the phone slightly,
 * which shifts gravity between axes. We downsample to 10 Hz, pick the axis that moves most,
 * band-pass 0.1 to 0.7 Hz (6 to 42 breaths/min) and use autocorrelation to find the period.
 */
class RespirationEstimator(private val fs: Double = 10.0, private val windowSec: Int = 40) {
    private val axes = Array(3) { ArrayDeque<Double>() }
    private val cap get() = (fs * windowSec).toInt()

    fun add(x: Double, y: Double, z: Double) {
        doubleArrayOf(x, y, z).forEachIndexed { i, v ->
            axes[i].addLast(v)
            if (axes[i].size > cap) axes[i].removeFirst()
        }
    }

    fun breathsPerMinute(): Int? {
        if (axes[0].size < fs * 20) return null // need at least 20 s
        val filtered = axes.map { a ->
            SignalMath.bandPass(a.toDoubleArray(), shortWin = (fs * 0.8).toInt(), longWin = (fs * 8).toInt())
        }
        val best = filtered.maxBy { SignalMath.std(it) }
        if (SignalMath.std(best) > 0.5) return null // too much motion, not resting
        val (lag, corr) = SignalMath.dominantLag(best, minLag = (fs * 60 / 42).toInt(), maxLag = (fs * 60 / 6).toInt()) ?: return null
        if (corr < 0.3) return null
        return (60.0 * fs / lag).toInt()
    }
}

data class PostureState(
    val faceTooClose: Boolean = false,
    val distanceCm: Float? = null, // only on phones whose proximity/ToF sensor reports real distance
    val pitchDeg: Float = 90f,
    val neckWarning: Boolean = false,
)

/** Rules kept pure so they can be unit tested. */
object PostureRules {
    const val MIN_SAFE_CM = 25f
    const val LOW_PITCH_DEG = 35f        // phone held low and flat means the head is bent down
    const val NECK_HOLD_MS = 60_000L

    /** Pitch from gravity: 90 = phone upright, 0 = lying flat face up. */
    fun pitchDegrees(y: Float, z: Float): Float =
        Math.toDegrees(atan2(y.toDouble(), sqrt((z * z).toDouble()))).toFloat()
}

