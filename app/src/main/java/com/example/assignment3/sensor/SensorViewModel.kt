package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneSensorUiState(
    val receivedBatchCount: Int = 0,
    val accelerometerFeatures: SensorFeatures? = null,
    val gyroscopeFeatures: SensorFeatures? = null,
    val motionState: MotionState = MotionState.UNKNOWN,
    val recordingLabel: String? = null,
    val recordedRowCount: Int = 0
)

class SensorViewModel(
    private val repository: SensorReceiveRepository,
    private val appContext: Context
) : ViewModel() {

    private val accelerometerWindow = RollingSampleWindow(WINDOW_NANOS, MAX_ACCELEROMETER_MAGNITUDE)
    private val gyroscopeWindow = RollingSampleWindow(WINDOW_NANOS, MAX_GYROSCOPE_MAGNITUDE)

    private val _uiState = MutableStateFlow(PhoneSensorUiState())
    val uiState: StateFlow<PhoneSensorUiState> = _uiState.asStateFlow()

    private var sessionId = 0
    private val pendingRows = mutableListOf<CalibrationRow>()

    init {
        viewModelScope.launch {
            repository.sensorBatches.collect { batch ->
                val current = _uiState.value
                val updated = when (batch.type) {
                    SensorReadingType.ACCELEROMETER -> current.copy(
                        receivedBatchCount = current.receivedBatchCount + 1,
                        accelerometerFeatures = SensorFeatureExtractor.extract(
                            accelerometerWindow.addAll(batch.samples),
                            GRAVITY_BASELINE
                        )
                    )
                    SensorReadingType.GYROSCOPE -> current.copy(
                        receivedBatchCount = current.receivedBatchCount + 1,
                        gyroscopeFeatures = SensorFeatureExtractor.extract(
                            gyroscopeWindow.addAll(batch.samples)
                        )
                    )
                }

                val latestFeatures = when (batch.type) {
                    SensorReadingType.ACCELEROMETER -> updated.accelerometerFeatures
                    SensorReadingType.GYROSCOPE -> updated.gyroscopeFeatures
                }
                val label = current.recordingLabel
                if (label != null && latestFeatures != null) {
                    pendingRows.add(CalibrationRow(sessionId, label, batch.type, latestFeatures))
                }

                _uiState.value = updated.copy(
                    motionState = MotionClassifier.classify(
                        updated.accelerometerFeatures,
                        updated.gyroscopeFeatures
                    ),
                    recordedRowCount = pendingRows.size
                )
            }
        }
    }

    fun startRecording(label: String) {
        sessionId += 1
        pendingRows.clear()
        _uiState.value = _uiState.value.copy(recordingLabel = label, recordedRowCount = 0)
    }

    fun stopRecording() {
        _uiState.value = _uiState.value.copy(recordingLabel = null)
        if (pendingRows.isNotEmpty()) {
            val rowsToWrite = pendingRows.toList()
            pendingRows.clear()
            viewModelScope.launch(Dispatchers.IO) {
                CalibrationLogger.append(appContext, rowsToWrite)
            }
        }
    }

    private companion object {
        const val WINDOW_NANOS = 2_000_000_000L
        const val GRAVITY_BASELINE = 9.80665f
        const val MAX_ACCELEROMETER_MAGNITUDE = 196.2f
        const val MAX_GYROSCOPE_MAGNITUDE = 34.9f
    }
}
