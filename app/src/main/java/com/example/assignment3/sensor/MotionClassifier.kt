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
 *
 * Replaced an earlier sequential-threshold chain after offline evaluation
 * against labeled calibration data (see DEVLOG) showed it plateaued around
 * 51% macro recall even after an exhaustive threshold grid search — tap,
 * shake, and walk overlap too much on any single feature to split with a
 * chain of independent thresholds. Using all dimensions jointly (still
 * untrained — just distance to each class's average feature vector, no
 * gradient descent) measured ~61% macro recall on the same data.
 *
 * Recalibrated after the sensor sampling rate moved from SENSOR_DELAY_GAME
 * (~50Hz) to a custom 100Hz (~69% macro recall), then again after adding
 * crossingIntervalCv (coefficient of variation of the gaps between
 * baseline crossings — low means evenly spaced like a walking cadence,
 * high means erratic like a shake), which pushed WALK's recall from ~68%
 * to ~97% and macro recall to ~73%, at the cost of SHAKE recall dropping
 * in that same calibration round (a gentler shake sample that session, not
 * a flaw in the feature itself — see DEVLOG).
 *
 * Known weak spot: a *gentle* shake and rapid tapping remain hard to tell
 * apart (both are small repeated impulses) — a firm, distinct shake
 * classifies far more reliably than a soft one. Retune FEATURE_MEANS/
 * FEATURE_STDS/CENTROIDS here as more labeled data comes in — and if the
 * sampling rate ever changes again, this must be recalibrated too.
 */
object MotionClassifier {

    // Order: accel[mean, stdDev, peakToPeak, zeroCrossing, energy, maxJerk,
    // crossingIntervalCv], then the same seven for gyro. Recalibrated at
    // 100Hz sampling with crossingIntervalCv added — the previous set
    // predates both the sampling-rate change and this feature (see DEVLOG).
    private val FEATURE_MEANS = floatArrayOf(
        9.98f, 0.317f, 2.7f, 8.585f, 99.843f, 1.233f, 0.652f,
        0.406f, 0.331f, 1.501f, 4.823f, 0.476f, 0.409f, 0.439f
    )
    private val FEATURE_STDS = floatArrayOf(
        0.131f, 0.346f, 2.219f, 6.081f, 2.784f, 0.895f, 0.452f,
        0.322f, 0.312f, 1.021f, 3.666f, 1.003f, 0.305f, 0.381f
    )

    private val CENTROIDS = mapOf(
        MotionState.STATIC to floatArrayOf(
            0.75f, 0.19f, 0.22f, -0.51f, 0.72f, 0.05f, -0.5f,
            -0.07f, -0.32f, -0.31f, 0.28f, -0.25f, 0.12f, 0.29f
        ),
        MotionState.TAP to floatArrayOf(
            0.7f, -0.12f, 0.03f, 0.16f, 0.61f, 1.07f, 0.08f,
            -0.09f, -0.03f, -0.04f, 0.12f, -0.19f, -0.16f, -0.13f
        ),
        MotionState.SHAKE to floatArrayOf(
            0.22f, 0.4f, 0.2f, -0.33f, 0.31f, -0.29f, -0.2f,
            0.57f, 0.7f, 0.65f, -0.07f, 0.75f, 0.45f, -0.03f
        ),
        MotionState.WALK to floatArrayOf(
            -1.5f, -0.43f, -0.4f, 0.6f, -1.46f, -0.74f, 0.54f,
            -0.4f, -0.36f, -0.31f, -0.29f, -0.32f, -0.39f, -0.11f
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
