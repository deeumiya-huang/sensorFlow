package com.example.assignment3.sensor

import com.example.assignment3.common.SensorSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes time-domain features from a window of samples, on the vector
 * magnitude sqrt(x^2+y^2+z^2) rather than raw axes, so results don't depend
 * on how the watch is worn/oriented.
 *
 * mean/stdDev/peakToPeak/zeroCrossingCount/energy are computed on a lightly
 * smoothed signal (denoise); maxJerk is computed on the raw, unsmoothed
 * signal since smoothing would blunt the sharp spike it's meant to detect.
 */
object SensorFeatureExtractor {

    private const val SMOOTHING_WINDOW = 5

    // Placeholder until Step C's on-device measurements tell us the real
    // sensor noise floor; suppresses crossings caused by noise jittering
    // right at the baseline rather than genuine motion.
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
        val zeroCrossingCount = smoothed.zipWithNext()
            .count { (a, b) -> (a - baseline) * (b - baseline) < 0 && abs(b - a) > crossingEpsilon }
        val maxJerk = rawMagnitudes.zipWithNext()
            .maxOfOrNull { (a, b) -> abs(b - a) } ?: 0f

        return SensorFeatures(
            sampleCount = samples.size,
            mean = mean,
            stdDev = sqrt(variance),
            peakToPeak = smoothed.max() - smoothed.min(),
            zeroCrossingCount = zeroCrossingCount,
            energy = smoothed.map { it * it }.average().toFloat(),
            maxJerk = maxJerk
        )
    }

    private fun movingAverage(values: List<Float>, windowSize: Int): List<Float> {
        if (values.size <= 1 || windowSize <= 1) return values
        return values.indices.map { i ->
            val start = (i - windowSize / 2).coerceAtLeast(0)
            val end = (i + windowSize / 2).coerceAtMost(values.lastIndex)
            values.subList(start, end + 1).average().toFloat()
        }
    }
}
