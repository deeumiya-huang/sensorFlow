package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class WatchMotionState { UNKNOWN, STATIC, TAP, SHAKE, WALK }

data class SensorUiState(
    val motionState: WatchMotionState = WatchMotionState.UNKNOWN,
    val isConnectionStalled: Boolean = false
)

class SensorViewModel(context: Context) : ViewModel() {

    private val repository = SensorRepository(context, viewModelScope)
    private val sender = SensorDataSender(context)
    private val motionStateReceiver = MotionStateReceiveRepository(context)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private val accelerometerSendMutex = Mutex()
    private val gyroscopeSendMutex = Mutex()
    private var lastSendSuccessAtMillis = System.currentTimeMillis()

    init {
        // Only the batching + send pipelines run now — the screen no longer
        // mirrors every raw 100Hz reading (it used to, via a separate
        // collector straight into _uiState), since the display now only
        // shows the phone's classified state and a connection light,
        // neither of which needs per-sample updates. That removes the
        // ~100/sec recomposition cost entirely rather than just throttling
        // it.
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
        viewModelScope.launch(Dispatchers.Default) {
            motionStateReceiver.motionStates.collect { state ->
                _uiState.value = _uiState.value.copy(motionState = state)
            }
        }
        // sendMessage is fire-and-forget with no delivery ack, so this
        // ticker is the only way to notice the phone link has gone quiet —
        // mirrors :app's own isStalled ticker (same threshold/interval).
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(STALL_CHECK_INTERVAL_MILLIS)
                val stalled = System.currentTimeMillis() - lastSendSuccessAtMillis > STALL_THRESHOLD_MILLIS
                if (stalled != _uiState.value.isConnectionStalled) {
                    _uiState.value = _uiState.value.copy(isConnectionStalled = stalled)
                }
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
                if (sender.send(type, samples)) {
                    lastSendSuccessAtMillis = System.currentTimeMillis()
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    private companion object {
        const val BATCH_WINDOW_MILLIS = 500L
        const val STALL_CHECK_INTERVAL_MILLIS = 500L
        const val STALL_THRESHOLD_MILLIS = 1500L
    }
}
