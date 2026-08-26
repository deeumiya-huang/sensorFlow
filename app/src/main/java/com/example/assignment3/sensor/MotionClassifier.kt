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
 * Recalibrated again with a WALK sample that includes natural arm swing
 * (the earlier one barely swung the arm at all, so its gyroscope readings
 * during a normal walk drifted into SHAKE territory live), and a
 * genuinely firm SHAKE (the previous one was mild enough that its own
 * centroid ended up *below* WALK's in magnitude — walk and shake looked
 * swapped live, since a real vigorous shake still legitimately dwarfs any
 * walk in the raw data).
 *
 * FEATURE_MEANS/FEATURE_STDS are deliberately computed excluding SHAKE's
 * rows: shake's magnitude is an order of magnitude larger than the other
 * three classes, and folding it into a shared mean/stdDev compresses
 * everyone else's z-scores together, making static/tap/walk harder to
 * separate from each other even though their raw values didn't change.
 * Normalizing against the other three, then locating SHAKE's (very
 * distant) centroid in that same space, avoided that trap — macro recall
 * on held-out data went from ~73% to ~78% this way, with static/tap/walk
 * essentially undisturbed. No per-dimension weighting was needed once this
 * fixed the normalization; a plain (unweighted) Euclidean distance was the
 * best-performing option this round.
 *
 * Static then started getting misread as WALK live: the WALK calibration
 * recording started with a calm pre-walking moment (same trap tap/shake
 * hit earlier — see DEVLOG), which pulled the WALK centroid toward
 * static's territory. Trimming that calm lead-in out of the WALK sample
 * before recalibrating pushed macro recall to ~83%, with all four classes
 * landing between 78-94% recall — no single class dominating at the
 * others' expense anymore.
 *
 * Retune FEATURE_MEANS/FEATURE_STDS/CENTROIDS here as more labeled data
 * comes in — and if the sampling rate ever changes again, this must be
 * recalibrated too.
 */
object MotionClassifier {

    // Order: accel[mean, stdDev, peakToPeak, zeroCrossing, energy, maxJerk,
    // crossingIntervalCv], then the same seven for gyro. Recalibrated at
    // 100Hz sampling with crossingIntervalCv added — the previous set
    // predates both the sampling-rate change and this feature (see DEVLOG).
    // Computed from STATIC + TAP + WALK only (SHAKE excluded — see class doc).
    // WALK's own calibration recording had its calm pre-walking moments
    // trimmed out first, since those were pulling live STATIC readings
    // toward the WALK centroid.
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
