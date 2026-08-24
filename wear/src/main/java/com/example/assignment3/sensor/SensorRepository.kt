package com.example.assignment3.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorRepository(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun accelerometerReadings(): Flow<SensorReading> =
        readingsFor(Sensor.TYPE_ACCELEROMETER, SensorReadingType.ACCELEROMETER)

    fun gyroscopeReadings(): Flow<SensorReading> =
        readingsFor(Sensor.TYPE_GYROSCOPE, SensorReadingType.GYROSCOPE)

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

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
}
