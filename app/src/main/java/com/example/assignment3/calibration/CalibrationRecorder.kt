package com.example.assignment3.calibration

import android.content.Context
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.sensor.SensorFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns a STATIC/TAP/SHAKE/WALK calibration-recording session: the current
 * label, the rows accumulated under it, and flushing them to
 * [CalibrationLogger] on stop. Pulled out of SensorViewModel so its main
 * sensor-processing path doesn't carry this test-only bookkeeping.
 */
class CalibrationRecorder {
    private var sessionId = 0
    private var currentLabel: String? = null
    private val pendingRows = mutableListOf<CalibrationRow>()

    val recordedRowCount: Int get() = pendingRows.size

    fun start(label: String) {
        sessionId += 1
        currentLabel = label
        pendingRows.clear()
    }

    fun record(type: SensorReadingType, features: SensorFeatures) {
        val label = currentLabel ?: return
        pendingRows.add(CalibrationRow(sessionId, label, type, features))
    }

    fun stopAndFlush(scope: CoroutineScope, appContext: Context) {
        currentLabel = null
        if (pendingRows.isEmpty()) return
        val rowsToWrite = pendingRows.toList()
        pendingRows.clear()
        scope.launch(Dispatchers.IO) {
            CalibrationLogger.append(appContext, rowsToWrite)
        }
    }
}
