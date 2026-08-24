package com.example.assignment3.sensor

enum class SensorReadingType {
    ACCELEROMETER,
    GYROSCOPE
}

data class SensorReading(
    val type: SensorReadingType,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long
)
