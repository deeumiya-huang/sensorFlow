package com.example.assignment3.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.assignment3.common.SensorReadingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

/**
 * [scope] backs the shared, hot sensor flows below (via [shareIn]) so that
 * multiple collectors can share a single SensorEventListener per sensor
 * instead of each registering its own.
 */
class SensorRepository(context: Context, scope: CoroutineScope) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val accelerometer: Flow<SensorReading> =
        readingsFor(Sensor.TYPE_ACCELEROMETER, SensorReadingType.ACCELEROMETER)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS))

    val gyroscope: Flow<SensorReading> =
        readingsFor(Sensor.TYPE_GYROSCOPE, SensorReadingType.GYROSCOPE)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS))

    private fun readingsFor(sensorType: Int, readingType: SensorReadingType): Flow<SensorReading> =
        callbackFlow {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor == null) {
                close()
                return@callbackFlow
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(
                        SensorReading(
                            type = readingType,
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2],
                            timestampNanos = event.timestamp
                        )
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            sensorManager.registerListener(listener, sensor, SAMPLING_PERIOD_US)
            awaitClose { sensorManager.unregisterListener(listener) }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // 100Hz (up from SENSOR_DELAY_GAME's ~50Hz): a tap's impact is a very
        // brief transient, and 50Hz was too coarse to reliably land a sample
        // near its true peak. Stays under 200Hz, so no
        // HIGH_SAMPLING_RATE_SENSORS permission is needed.
        const val SAMPLING_PERIOD_US = 10_000
    }
}
