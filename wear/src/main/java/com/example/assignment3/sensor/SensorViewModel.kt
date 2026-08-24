package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SensorUiState(
    val accelerometer: SensorReading? = null,
    val gyroscope: SensorReading? = null
)

class SensorViewModel(context: Context) : ViewModel() {

    private val repository = SensorRepository(context, viewModelScope)
    private val sender = SensorDataSender(context)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.accelerometer.collect { reading ->
                _uiState.value = _uiState.value.copy(accelerometer = reading)
            }
        }
        viewModelScope.launch {
            repository.gyroscope.collect { reading ->
                _uiState.value = _uiState.value.copy(gyroscope = reading)
            }
        }
        viewModelScope.launch {
            repository.accelerometer.chunkedByTime(BATCH_WINDOW_MILLIS).collect { batch ->
                sender.send(SensorReadingType.ACCELEROMETER, batch.map { it.toSample() })
            }
        }
        viewModelScope.launch {
            repository.gyroscope.chunkedByTime(BATCH_WINDOW_MILLIS).collect { batch ->
                sender.send(SensorReadingType.GYROSCOPE, batch.map { it.toSample() })
            }
        }
    }

    private companion object {
        const val BATCH_WINDOW_MILLIS = 500L
    }
}
