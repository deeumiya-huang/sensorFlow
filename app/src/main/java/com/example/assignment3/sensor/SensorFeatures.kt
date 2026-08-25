package com.example.assignment3.sensor

data class SensorFeatures(
    val sampleCount: Int,
    val mean: Float,
    val stdDev: Float,
    val peakToPeak: Float,
    val zeroCrossingCount: Int,
    val energy: Float,
    val maxJerk: Float
)
