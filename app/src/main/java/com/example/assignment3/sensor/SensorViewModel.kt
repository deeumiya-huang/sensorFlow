package com.example.assignment3.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneSensorUiState(
    val receivedBatchCount: Int = 0,
    val accelerometerFeatures: SensorFeatures? = null,
    val gyroscopeFeatures: SensorFeatures? = null
)

class SensorViewModel(private val repository: SensorReceiveRepository) : ViewModel() {

    private val accelerometerWindow = RollingSampleWindow(WINDOW_NANOS, MAX_ACCELEROMETER_MAGNITUDE)
    private val gyroscopeWindow = RollingSampleWindow(WINDOW_NANOS, MAX_GYROSCOPE_MAGNITUDE)

    private val _uiState = MutableStateFlow(PhoneSensorUiState())
    val uiState: StateFlow<PhoneSensorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sensorBatches.collect { batch ->
                val current = _uiState.value
                _uiState.value = when (batch.type) {
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
            }
        }
    }

    private companion object {
        const val WINDOW_NANOS = 2_000_000_000L
        const val GRAVITY_BASELINE = 9.80665f

        // Generous headroom above any real wrist tap/shake (~3-6g); only
        // catches hardware glitches / corrupted transmissions, not motion.
        const val MAX_ACCELEROMETER_MAGNITUDE = 196.2f // ~20g
        const val MAX_GYROSCOPE_MAGNITUDE = 34.9f // ~2000 deg/s
    }
}
