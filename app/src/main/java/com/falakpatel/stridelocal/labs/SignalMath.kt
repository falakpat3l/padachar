package com.falakpatel.stridelocal.labs

import kotlin.math.sqrt

/** Small DSP helpers shared by the Labs modules. Pure Kotlin, unit tested. */
object SignalMath {
    fun movingAverage(x: DoubleArray, window: Int): DoubleArray {
        if (window <= 1 || x.isEmpty()) return x.copyOf()
        val out = DoubleArray(x.size)
        var sum = 0.0
        for (i in x.indices) {
            sum += x[i]
            if (i >= window) sum -= x[i - window]
            out[i] = sum / minOf(i + 1, window)
        }
        return out
    }

    /** Band-pass by difference of moving averages: smooth short, subtract smooth long. */
    fun bandPass(x: DoubleArray, shortWin: Int, longWin: Int): DoubleArray {
        val s = movingAverage(x, shortWin)
        val l = movingAverage(x, longWin)
        return DoubleArray(x.size) { s[it] - l[it] }
    }

    fun std(x: DoubleArray): Double {
        if (x.isEmpty()) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / x.size)
    }

    /** Normalised autocorrelation at [lag] (between -1 and 1). */
    fun autocorr(x: DoubleArray, lag: Int): Double {
        val m = x.average()
        var num = 0.0
        var den = 0.0
        for (i in x.indices) {
            val d = x[i] - m
            den += d * d
            if (i + lag < x.size) num += d * (x[i + lag] - m)
        }
        return if (den == 0.0) 0.0 else num / den
    }

    /** Dominant period in samples, searched between [minLag] and [maxLag]. Returns lag to correlation. */
    fun dominantLag(x: DoubleArray, minLag: Int, maxLag: Int): Pair<Int, Double>? {
        var best: Pair<Int, Double>? = null
        for (lag in minLag..minOf(maxLag, x.size / 2)) {
            val c = autocorr(x, lag)
            if (best == null || c > best.second) best = lag to c
        }
        return best
    }
}
