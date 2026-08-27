package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.calibration.CalibrationLogger
import com.example.assignment3.calibration.CalibrationRow
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class PhoneSensorUiState(
    val receivedBatchCount: Int = 0,
    val accelerometerFeatures: SensorFeatures? = null,
    val gyroscopeFeatures: SensorFeatures? = null,
    val accelerometerMagnitudeHistory: List<Float> = emptyList(),
    val accelerometerSmoothedHistory: List<Float> = emptyList(),
    val gyroscopeMagnitudeHistory: List<Float> = emptyList(),
    val gyroscopeSmoothedHistory: List<Float> = emptyList(),
    val motionState: MotionState = MotionState.UNKNOWN,
    val isStalled: Boolean = false,
    val recordingLabel: String? = null,
    val recordedRowCount: Int = 0
)

class SensorViewModel(
    private val repository: SensorReceiveRepository,
    private val appContext: Context
) : ViewModel() {

    private val accelerometerWindow = RollingSampleWindow(WINDOW_NANOS, MAX_ACCELEROMETER_MAGNITUDE)
    private val gyroscopeWindow = RollingSampleWindow(WINDOW_NANOS, MAX_GYROSCOPE_MAGNITUDE)
    private val motionStateSender = MotionStateSender(appContext)

    private val _uiState = MutableStateFlow(PhoneSensorUiState())
    val uiState: StateFlow<PhoneSensorUiState> = _uiState.asStateFlow()

    private var sessionId = 0
    private val pendingRows = mutableListOf<CalibrationRow>()
    private var lastBatchAtMillis = System.currentTimeMillis()

    // A raw per-batch classification flickers whenever features sit near a
    // class boundary (e.g. STATIC/WALK during a transition). Only committing
    // a new displayed state once it has repeated STABLE_STREAK_REQUIRED times
    // in a row filters that flicker out without touching the classifier
    // itself — a single stray reading can no longer flip the label.
    private var pendingMotionState: MotionState? = null
    private var pendingMotionStreak: Int = 0

    private fun stabilizedMotionState(rawState: MotionState, displayedState: MotionState): MotionState {
        if (rawState == displayedState) {
            pendingMotionState = null
            pendingMotionStreak = 0
            return displayedState
        }
        if (rawState == pendingMotionState) {
            pendingMotionStreak++
        } else {
            pendingMotionState = rawState
            pendingMotionStreak = 1
        }
        if (pendingMotionStreak < STABLE_STREAK_REQUIRED) return displayedState
        pendingMotionState = null
        pendingMotionStreak = 0
        return rawState
    }

    init {
        // Runs on Dispatchers.Default, not the main thread: at 100Hz this
        // fires up to ~200 times/sec combined across both sensors, and must
        // never compete with Compose recomposition (chart redraws, the
        // pixel-art animation) for the main thread — if it did, a UI frame
        // running long would delay draining incoming data, and the backlog
        // would compound worse the longer the app runs instead of settling.
        viewModelScope.launch(Dispatchers.Default) {
            repository.sensorBatches.collect { batch ->
                lastBatchAtMillis = System.currentTimeMillis()
                val current = _uiState.value
                val updated = when (batch.type) {
                    SensorReadingType.ACCELEROMETER -> {
                        val windowed = accelerometerWindow.addAll(batch.samples)
                        val magnitudes = magnitudesOf(windowed)
                        current.copy(
                            receivedBatchCount = current.receivedBatchCount + 1,
                            accelerometerFeatures = SensorFeatureExtractor.extract(windowed, GRAVITY_BASELINE),
                            accelerometerMagnitudeHistory = magnitudes,
                            accelerometerSmoothedHistory = SensorFeatureExtractor.smooth(magnitudes),
                            isStalled = false
                        )
                    }
                    SensorReadingType.GYROSCOPE -> {
                        val windowed = gyroscopeWindow.addAll(batch.samples)
                        val magnitudes = magnitudesOf(windowed)
                        current.copy(
                            receivedBatchCount = current.receivedBatchCount + 1,
                            gyroscopeFeatures = SensorFeatureExtractor.extract(windowed),
                            gyroscopeMagnitudeHistory = magnitudes,
                            gyroscopeSmoothedHistory = SensorFeatureExtractor.smooth(magnitudes),
                            isStalled = false
                        )
                    }
                }

                val latestFeatures = when (batch.type) {
                    SensorReadingType.ACCELEROMETER -> updated.accelerometerFeatures
                    SensorReadingType.GYROSCOPE -> updated.gyroscopeFeatures
                }
                val label = current.recordingLabel
                if (label != null && latestFeatures != null) {
                    pendingRows.add(CalibrationRow(sessionId, label, batch.type, latestFeatures))
                }

                val rawMotionState = MotionClassifier.classify(
                    updated.accelerometerFeatures,
                    updated.gyroscopeFeatures
                )
                val stableMotionState = stabilizedMotionState(rawMotionState, current.motionState)
                if (stableMotionState != current.motionState) {
                    // Only pushed to the watch on an actual change, not every
                    // batch — the watch doesn't need a running commentary,
                    // just to know when the displayed state flips.
                    launch(Dispatchers.Default) { motionStateSender.send(stableMotionState) }
                }
                _uiState.value = updated.copy(
                    motionState = stableMotionState,
                    recordedRowCount = pendingRows.size
                )
            }
        }

        // No batch-arrival event fires while the transport is stalled, so a
        // separate ticker is needed to notice the silence and surface it.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(STALL_CHECK_INTERVAL_MILLIS)
                val stalled = System.currentTimeMillis() - lastBatchAtMillis > STALL_THRESHOLD_MILLIS
                if (stalled != _uiState.value.isStalled) {
                    _uiState.value = _uiState.value.copy(isStalled = stalled)
                }
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

    private fun magnitudesOf(samples: List<SensorSample>): List<Float> =
        samples.map { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }

    companion object {
        const val WINDOW_NANOS = 2_000_000_000L
        private const val GRAVITY_BASELINE = 9.80665f
        private const val MAX_ACCELEROMETER_MAGNITUDE = 196.2f
        private const val MAX_GYROSCOPE_MAGNITUDE = 34.9f
        private const val STALL_CHECK_INTERVAL_MILLIS = 500L
        private const val STALL_THRESHOLD_MILLIS = 1500L
        private const val STABLE_STREAK_REQUIRED = 2
    }
}
