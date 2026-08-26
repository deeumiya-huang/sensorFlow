package com.example.assignment3.sensor

import android.content.Context
import com.example.assignment3.common.SensorReadingType
import java.io.File

data class CalibrationRow(
    val sessionId: Int,
    val label: String,
    val sensorType: SensorReadingType,
    val features: SensorFeatures
)

/**
 * Appends labeled feature snapshots to a CSV on the phone's app-specific
 * external storage, so a whole recording session can be pulled off-device
 * (`adb pull`) and analyzed precisely instead of hand-copying numbers off a
 * live, flickering debug screen.
 */
object CalibrationLogger {
    const val FILE_NAME = "calibration_log.csv"
    private const val HEADER =
        "sessionId,label,sensorType,sampleCount,mean,stdDev,peakToPeak,zeroCrossing,energy,maxJerk,crossingIntervalCv"

    fun append(context: Context, rows: List<CalibrationRow>) {
        if (rows.isEmpty()) return
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val needsHeader = !file.exists()
        file.appendText(
            buildString {
                if (needsHeader) appendLine(HEADER)
                for (row in rows) {
                    val f = row.features
                    appendLine(
                        listOf(
                            row.sessionId, row.label, row.sensorType,
                            f.sampleCount, f.mean, f.stdDev, f.peakToPeak,
                            f.zeroCrossingCount, f.energy, f.maxJerk, f.crossingIntervalCv
                        ).joinToString(",")
                    )
                }
            }
        )
    }
}
