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
 * Nearest-centroid classifier over 12 z-normalized features (6 time-domain
 * features each from accelerometer and gyroscope magnitude: mean, stdDev,
 * peakToPeak, zeroCrossingCount, energy, maxJerk).
 *
 * Replaced an earlier sequential-threshold chain after offline evaluation
 * against labeled calibration data (see DEVLOG) showed it plateaued around
 * 51% macro recall even after an exhaustive threshold grid search — tap,
 * shake, and walk overlap too much on any single feature to split with a
 * chain of independent thresholds. Using all 12 dimensions jointly (still
 * untrained — just distance to each class's average feature vector, no
 * gradient descent) measured ~61% macro recall on the same data.
 *
 * Known weak spot: a *gentle* shake and rapid tapping remain hard to tell
 * apart (both are small repeated impulses) — a firm, distinct shake
 * classifies far more reliably than a soft one. Retune FEATURE_MEANS/
 * FEATURE_STDS/CENTROIDS here as more labeled data comes in.
 */
object MotionClassifier {

    // Order: accel[mean, stdDev, peakToPeak, zeroCrossing, energy, maxJerk],
    // then the same six for gyro.
    private val FEATURE_MEANS = floatArrayOf(
        10.385f, 1.141f, 11.378f, 7.814f, 112.581f, 6.79f,
        1.111f, 0.548f, 2.939f, 5.254f, 5.803f, 1.429f
    )
    private val FEATURE_STDS = floatArrayOf(
        0.825f, 1.657f, 10.028f, 5.746f, 30.266f, 5.391f,
        1.942f, 0.706f, 3.588f, 3.38f, 15.396f, 1.786f
    )

    private val CENTROIDS = mapOf(
        MotionState.STATIC to floatArrayOf(
            -0.38f, -0.6f, -0.86f, -0.65f, -0.37f, -0.84f,
            -0.4f, -0.29f, -0.3f, -0.35f, -0.36f, -0.25f
        ),
        MotionState.TAP to floatArrayOf(
            -0.26f, -0.34f, -0.11f, 0.06f, -0.29f, 0.05f,
            -0.28f, -0.41f, -0.34f, 0.53f, -0.26f, -0.12f
        ),
        MotionState.SHAKE to floatArrayOf(
            0.33f, 0.36f, 0.55f, 0.43f, 0.35f, 0.61f,
            0.23f, 0.11f, 0.18f, 0.14f, 0.33f, 0.3f
        ),
        MotionState.WALK to floatArrayOf(
            -0.18f, -0.06f, -0.43f, -0.45f, -0.2f, -0.65f,
            0.02f, 0.29f, 0.1f, -0.46f, -0.19f, -0.3f
        )
    )

    fun classify(accelerometer: SensorFeatures?, gyroscope: SensorFeatures?): MotionState {
        if (accelerometer == null) return MotionState.UNKNOWN

        val raw = floatArrayOf(
            accelerometer.mean, accelerometer.stdDev, accelerometer.peakToPeak,
            accelerometer.zeroCrossingCount.toFloat(), accelerometer.energy, accelerometer.maxJerk,
            gyroscope?.mean ?: FEATURE_MEANS[6],
            gyroscope?.stdDev ?: FEATURE_MEANS[7],
            gyroscope?.peakToPeak ?: FEATURE_MEANS[8],
            gyroscope?.zeroCrossingCount?.toFloat() ?: FEATURE_MEANS[9],
            gyroscope?.energy ?: FEATURE_MEANS[10],
            gyroscope?.maxJerk ?: FEATURE_MEANS[11]
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
