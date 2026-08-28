package com.example.assignment3.sensor

import kotlin.math.sqrt

enum class MotionState {
    UNKNOWN,
    STATIC,
    TAP,
    SHAKE,
    WALK
}

/**
 * Nearest-centroid classifier over 14 z-normalized features (7 time-domain
 * features each from accelerometer and gyroscope magnitude: mean, stdDev,
 * peakToPeak, zeroCrossingCount, energy, maxJerk, crossingIntervalCv).
 * Untrained — just Euclidean distance to each class's average feature
 * vector, no gradient descent.
 *
 * FEATURE_MEANS/FEATURE_STDS are deliberately computed excluding SHAKE's
 * rows: shake's magnitude is an order of magnitude larger than the other
 * three classes, and folding it into a shared mean/stdDev compresses
 * everyone else's z-scores together, making static/tap/walk harder to
 * separate from each other even though their raw values didn't change.
 * SHAKE's own centroid is still located in that same normalized space —
 * it just doesn't contribute to computing it.
 *
 * Calibrated for the 100Hz sampling rate in SensorRepository — features
 * like maxJerk and zeroCrossingCount are rate-sensitive, so
 * FEATURE_MEANS/FEATURE_STDS/CENTROIDS need to be recalibrated against
 * fresh labeled data if that rate ever changes.
 */
object MotionClassifier {

    // Order: accel[mean, stdDev, peakToPeak, zeroCrossing, energy, maxJerk,
    // crossingIntervalCv], then the same seven for gyro. Computed from
    // STATIC + TAP + WALK only (SHAKE excluded — see class doc). If
    // re-recording calibration data, trim any calm/stationary lead-in out of
    // the WALK sample first — it otherwise pulls the WALK centroid toward
    // STATIC's and causes live confusion between the two.
    private val FEATURE_MEANS = floatArrayOf(
        10.168f, 0.643f, 4.165f, 6.705f, 105.103f, 1.625f, 0.535f,
        0.634f, 0.523f, 2.069f, 5.068f, 1.262f, 0.433f, 0.468f
    )
    private val FEATURE_STDS = floatArrayOf(
        0.581f, 0.977f, 4.526f, 5.303f, 22.093f, 1.09f, 0.391f,
        0.554f, 0.528f, 1.626f, 3.496f, 2.13f, 0.243f, 0.338f
    )

    private val CENTROIDS = mapOf(
        MotionState.STATIC to floatArrayOf(
            -0.15f, -0.27f, -0.22f, -0.23f, -0.15f, -0.32f, -0.29f,
            -0.45f, -0.56f, -0.55f, 0.23f, -0.49f, 0.06f, 0.25f
        ),
        MotionState.TAP to floatArrayOf(
            -0.16f, -0.38f, -0.31f, 0.54f, -0.16f, 0.52f, 0.39f,
            -0.46f, -0.38f, -0.37f, 0.05f, -0.46f, -0.3f, -0.23f
        ),
        MotionState.SHAKE to floatArrayOf(
            10.2f, 8.25f, 7.78f, 0.75f, 10.78f, 5.07f, 0.16f,
            12.99f, 4.56f, 6.61f, 1.75f, 33.03f, 7.19f, -0.8f
        ),
        MotionState.WALK to floatArrayOf(
            0.38f, 0.77f, 0.63f, -0.39f, 0.37f, -0.26f, -0.16f,
            1.09f, 1.11f, 1.09f, -0.33f, 1.13f, 0.3f, 0.0f
        )
    )

    fun classify(accelerometer: SensorFeatures?, gyroscope: SensorFeatures?): MotionState {
        if (accelerometer == null) return MotionState.UNKNOWN

        val raw = floatArrayOf(
            accelerometer.mean, accelerometer.stdDev, accelerometer.peakToPeak,
            accelerometer.zeroCrossingCount.toFloat(), accelerometer.energy, accelerometer.maxJerk,
            accelerometer.crossingIntervalCv,
            gyroscope?.mean ?: FEATURE_MEANS[7],
            gyroscope?.stdDev ?: FEATURE_MEANS[8],
            gyroscope?.peakToPeak ?: FEATURE_MEANS[9],
            gyroscope?.zeroCrossingCount?.toFloat() ?: FEATURE_MEANS[10],
            gyroscope?.energy ?: FEATURE_MEANS[11],
            gyroscope?.maxJerk ?: FEATURE_MEANS[12],
            gyroscope?.crossingIntervalCv ?: FEATURE_MEANS[13]
        )

        val normalized = FloatArray(raw.size) { i -> (raw[i] - FEATURE_MEANS[i]) / FEATURE_STDS[i] }

        return CENTROIDS.entries
            .minByOrNull { (_, centroid) -> distance(normalized, centroid) }
            ?.key ?: MotionState.UNKNOWN
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }
}
