package com.example.assignment3.sensor

import com.example.assignment3.common.SensorSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes time-domain features from a window of samples, on the vector
 * magnitude sqrt(x^2+y^2+z^2) rather than raw axes, so results don't depend
 * on how the watch is worn/oriented.
 *
 * mean/stdDev/zeroCrossingCount/energy/crossingIntervalCv are computed on a
 * lightly smoothed signal (denoise); peakToPeak and maxJerk are computed on
 * the raw, unsmoothed signal since smoothing would blunt the sharp spike
 * they're meant to detect (e.g. a tap).
 */
object SensorFeatureExtractor {

    private const val SMOOTHING_WINDOW = 5

    // Suppresses crossings caused by sensor noise jittering right at the
    // baseline rather than genuine motion.
    private const val DEFAULT_CROSSING_EPSILON = 0.05f

    /**
     * [crossingBaseline] is the reference value zero-crossing is measured
     * against. Pass a fixed physical constant when one exists (e.g. gravity
     * for accelerometer magnitude); leave it null to fall back to this
     * window's own mean (e.g. gyroscope magnitude, which is bounded at 0 and
     * so never legitimately crosses a fixed 0 baseline).
     */
    fun extract(
        samples: List<SensorSample>,
        crossingBaseline: Float? = null,
        crossingEpsilon: Float = DEFAULT_CROSSING_EPSILON
    ): SensorFeatures? {
        if (samples.isEmpty()) return null

        val rawMagnitudes = samples.map { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }
        val smoothed = movingAverage(rawMagnitudes, SMOOTHING_WINDOW)

        val mean = smoothed.average().toFloat()
        val variance = smoothed.map { (it - mean) * (it - mean) }.average().toFloat()
        val baseline = crossingBaseline ?: mean
        val crossingIndices = smoothed.withIndex().zipWithNext()
            .filter { (a, b) -> (a.value - baseline) * (b.value - baseline) < 0 && abs(b.value - a.value) > crossingEpsilon }
            .map { (a, _) -> a.index }
        val maxJerk = rawMagnitudes.zipWithNext()
            .maxOfOrNull { (a, b) -> abs(b - a) } ?: 0f

        return SensorFeatures(
            sampleCount = samples.size,
            mean = mean,
            stdDev = sqrt(variance),
            peakToPeak = rawMagnitudes.max() - rawMagnitudes.min(),
            zeroCrossingCount = crossingIndices.size,
            energy = smoothed.map { it * it }.average().toFloat(),
            maxJerk = maxJerk,
            crossingIntervalCv = crossingIntervalCv(crossingIndices)
        )
    }

    private fun crossingIntervalCv(crossingIndices: List<Int>): Float {
        val intervals = crossingIndices.zipWithNext { a, b -> (b - a).toFloat() }
        if (intervals.size < 2) return 0f
        val mean = intervals.average().toFloat()
        if (mean <= 0f) return 0f
        val variance = intervals.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance) / mean
    }

    /** Exposes the same smoothing used internally, for charting purposes. */
    fun smooth(values: List<Float>): List<Float> = movingAverage(values, SMOOTHING_WINDOW)

    private fun movingAverage(values: List<Float>, windowSize: Int): List<Float> {
        if (values.size <= 1 || windowSize <= 1) return values
        return values.indices.map { i ->
            val start = (i - windowSize / 2).coerceAtLeast(0)
            val end = (i + windowSize / 2).coerceAtMost(values.lastIndex)
            values.subList(start, end + 1).average().toFloat()
        }
    }
}
