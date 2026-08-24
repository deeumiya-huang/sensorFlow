package com.example.assignment3.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneSensorUiState(
    val latestAccelerometerBatch: List<SensorSample> = emptyList(),
    val latestGyroscopeBatch: List<SensorSample> = emptyList(),
    val receivedBatchCount: Int = 0
)

class SensorViewModel(private val repository: SensorReceiveRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneSensorUiState())
    val uiState: StateFlow<PhoneSensorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sensorBatches.collect { batch ->
                val current = _uiState.value
                _uiState.value = when (batch.type) {
                    SensorReadingType.ACCELEROMETER -> current.copy(
                        latestAccelerometerBatch = batch.samples,
                        receivedBatchCount = current.receivedBatchCount + 1
                    )
                    SensorReadingType.GYROSCOPE -> current.copy(
                        latestGyroscopeBatch = batch.samples,
                        receivedBatchCount = current.receivedBatchCount + 1
                    )
                }
            }
        }
    }
}
