package com.example.assignment3.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SensorUiState(
    val accelerometer: SensorReading? = null,
    val gyroscope: SensorReading? = null
)

class SensorViewModel(private val repository: SensorRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.accelerometerReadings().collect { reading ->
                _uiState.value = _uiState.value.copy(accelerometer = reading)
            }
        }
        viewModelScope.launch {
            repository.gyroscopeReadings().collect { reading ->
                _uiState.value = _uiState.value.copy(gyroscope = reading)
            }
        }
    }
}
