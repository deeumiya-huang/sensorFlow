package com.example.assignment3.sensor

import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample

data class SensorReading(
    val type: SensorReadingType,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long
)

fun SensorReading.toSample(): SensorSample = SensorSample(x, y, z, timestampNanos)
