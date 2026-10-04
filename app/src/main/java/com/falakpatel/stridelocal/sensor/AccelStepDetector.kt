package com.falakpatel.stridelocal.sensor

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fallback pedometer for phones without a hardware step counter.
 *
 * Pipeline per accelerometer sample (about 50 Hz):
 *  1. Magnitude |a| = sqrt(x^2 + y^2 + z^2), so phone orientation does not matter.
 *  2. High-pass: subtract a slow moving average (the gravity/DC part, cutoff about 0.15 Hz).
 *  3. Low-pass: exponential smoothing removes jitter above about 3 Hz.
 *  4. Peak detection: a local maximum counts when its peak-to-valley swing beats a
 *     dynamic threshold (half of the recent average swing, never below a floor).
 *  5. Timing gate: 250 ms to 2 s between peaks (30 to 240 steps/min).
 *  6. Rhythm confirmation: steps are only released after 4 regular peaks in a row,
 *     so a single bump (phone put on a table) is ignored.
 *
 * Pure Kotlin so it is unit tested with synthetic signals.
 */
class AccelStepDetector(
    private val minIntervalMs: Long = 250,
    private val maxIntervalMs: Long = 2_000,
    private val minSwing: Double = 1.0,
    private val confirmSteps: Int = 4,
) {
    private var initialized = false
    private var gravity = 0.0
    private var smoothed = 0.0
    private var prev = 0.0
    private var rising = false
    private var lastValley = 0.0
    private var avgSwing = 0.0
    private var lastPeakMs = -1L
    private var run = 0

    /** @return number of steps confirmed by this sample (usually 0 or 1, or [confirmSteps] once). */
    fun onSample(timeMs: Long, x: Float, y: Float, z: Float): Int {
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        if (!initialized) {
            gravity = mag
            initialized = true
            return 0
        }
        gravity += 0.02 * (mag - gravity)          // slow average = gravity estimate
        smoothed += 0.3 * ((mag - gravity) - smoothed) // low-pass on the dynamic part

        var steps = 0
        val nowRising = smoothed > prev
        if (rising && !nowRising) steps = onPeak(timeMs, prev)   // prev sample was a local max
        if (!rising && nowRising) lastValley = prev               // prev sample was a local min
        rising = nowRising
        prev = smoothed
        return steps
    }

    private fun onPeak(timeMs: Long, peak: Double): Int {
        val swing = peak - lastValley
        val threshold = max(minSwing, 0.5 * avgSwing)
        if (swing < threshold) return 0
        val dt = if (lastPeakMs < 0) Long.MAX_VALUE else timeMs - lastPeakMs
        if (dt < minIntervalMs) return 0 // bounce inside one step

        if (dt > maxIntervalMs) {        // rhythm broken: start a new candidate run
            run = 1
            avgSwing = swing
            lastPeakMs = timeMs
            return 0
        }
        avgSwing = 0.8 * avgSwing + 0.2 * swing // adapts to walking intensity
        lastPeakMs = timeMs
        run++
        return when {
            run == confirmSteps -> confirmSteps // release the buffered steps
            run > confirmSteps -> 1
            else -> 0
        }
    }
}
