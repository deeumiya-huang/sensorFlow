package com.example.assignment3.sensor

data class SensorFeatures(
    val sampleCount: Int,
    val mean: Float,
    val stdDev: Float,
    val peakToPeak: Float,
    val zeroCrossingCount: Int,
    val energy: Float,
    val maxJerk: Float,
    // Coefficient of variation of the gaps between crossings: low = evenly
    // spaced (e.g. a walking cadence), high = erratic (e.g. a shake). 0 when
    // there are fewer than 2 gaps to compare (not enough crossings to judge).
    val crossingIntervalCv: Float
)
