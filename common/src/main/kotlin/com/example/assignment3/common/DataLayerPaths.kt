package com.example.assignment3.common

object DataLayerPaths {
    const val ACCELEROMETER_BATCH = "/sensor/accelerometer"
    const val GYROSCOPE_BATCH = "/sensor/gyroscope"

    // Phone -> watch: the classified motion state, sent as the state name's
    // raw UTF-8 bytes (e.g. "WALK"). One-way and infrequent, so it doesn't
    // need SensorBatchCodec's binary framing.
    const val MOTION_STATE = "/motion/state"

    fun pathFor(type: SensorReadingType): String = when (type) {
        SensorReadingType.ACCELEROMETER -> ACCELEROMETER_BATCH
        SensorReadingType.GYROSCOPE -> GYROSCOPE_BATCH
    }

    fun typeForPath(path: String): SensorReadingType? = when (path) {
        ACCELEROMETER_BATCH -> SensorReadingType.ACCELEROMETER
        GYROSCOPE_BATCH -> SensorReadingType.GYROSCOPE
        else -> null
    }
}
