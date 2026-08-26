package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class SensorUiState(
    val accelerometer: SensorReading? = null,
    val gyroscope: SensorReading? = null
)

class SensorViewModel(context: Context) : ViewModel() {

    private val repository = SensorRepository(context, viewModelScope)
    private val sender = SensorDataSender(context)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private val accelerometerSendMutex = Mutex()
    private val gyroscopeSendMutex = Mutex()

    init {
        // All four run on Dispatchers.Default, not the main thread: at 100Hz
        // these fire far too often (raw readings) or do network I/O
        // (batching + send) to share a thread with the watch's own screen
        // recomposition — if a frame ran long, it would delay sending data
        // to the phone, which is exactly what looks like a transport stall
        // downstream (see DEVLOG; this is the same bug already fixed on the
        // phone side, just never applied here).
        viewModelScope.launch(Dispatchers.Default) {
            repository.accelerometer.collect { reading ->
                _uiState.value = _uiState.value.copy(accelerometer = reading)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            repository.gyroscope.collect { reading ->
                _uiState.value = _uiState.value.copy(gyroscope = reading)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            repository.accelerometer.chunkedByTime(BATCH_WINDOW_MILLIS).collect { batch ->
                sendNonBlocking(accelerometerSendMutex, SensorReadingType.ACCELEROMETER, batch.map { it.toSample() })
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            repository.gyroscope.chunkedByTime(BATCH_WINDOW_MILLIS).collect { batch ->
                sendNonBlocking(gyroscopeSendMutex, SensorReadingType.GYROSCOPE, batch.map { it.toSample() })
            }
        }
    }

    /**
     * Fires the send as a child coroutine instead of awaiting it inline, so a
     * slow or stuck send can't hold up this collector from picking up the
     * next batch. [mutex] caps this at one in-flight send per sensor: if the
     * previous send hasn't finished, this batch is dropped rather than
     * piling up concurrent sends (which would only make real congestion
     * worse) — consistent with chunkedByTime's own drop-oldest policy.
     */
    private fun CoroutineScope.sendNonBlocking(
        mutex: Mutex,
        type: SensorReadingType,
        samples: List<SensorSample>
    ) {
        if (!mutex.tryLock()) return
        launch {
            try {
                sender.send(type, samples)
            } finally {
                mutex.unlock()
            }
        }
    }

    private companion object {
        const val BATCH_WINDOW_MILLIS = 500L
    }
}
