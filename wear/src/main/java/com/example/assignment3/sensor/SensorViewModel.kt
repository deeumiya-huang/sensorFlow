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
import kotlinx.coroutines.flow.update
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

    // sendMessage() only confirms the message reached the paired device, not
    // that a live app on the other end consumed it — a dead phone app still
    // lets sends "succeed", so lastSendSuccessAtMillis alone can't detect
    // that. This tracks the phone's periodic motion-state heartbeat instead,
    // which only keeps arriving while the phone app is actually alive and
    // receiving from us — the same kind of signal :app's own isStalled uses.
    private var lastMotionStateReceivedAtMillis = System.currentTimeMillis()

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
                lastMotionStateReceivedAtMillis = System.currentTimeMillis()
                _uiState.update { it.copy(motionState = state) }
            }
        }
        // Stalled if EITHER our own sends have stopped succeeding (can't
        // reach the phone at all) OR the phone's heartbeat has gone quiet
        // (reachable, but nothing alive on the other end) — the two catch
        // different failure modes, neither implies the other.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(STALL_CHECK_INTERVAL_MILLIS)
                val now = System.currentTimeMillis()
                val stalled = now - lastSendSuccessAtMillis > STALL_THRESHOLD_MILLIS ||
                    now - lastMotionStateReceivedAtMillis > STALL_THRESHOLD_MILLIS
                _uiState.update { latest ->
                    when {
                        stalled == latest.isConnectionStalled -> latest
                        stalled -> latest.copy(isConnectionStalled = true, motionState = WatchMotionState.UNKNOWN)
                        else -> latest.copy(isConnectionStalled = false)
                    }
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
