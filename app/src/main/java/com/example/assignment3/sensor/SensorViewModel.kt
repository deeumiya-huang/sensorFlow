package com.example.assignment3.sensor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment3.calibration.CalibrationRecorder
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    // Calibration-recording status (STATIC/TAP/SHAKE/WALK label logging used
    // to derive MotionClassifier's thresholds — see CalibrationRecorder).
    // Compose has to observe these directly, so unlike the rest of that
    // logic they can't move out of this state class with it.
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

    private val calibrationRecorder = CalibrationRecorder()
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
                if (latestFeatures != null) {
                    calibrationRecorder.record(batch.type, latestFeatures)
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

                // Re-applies this batch's already-computed fields onto
                // whatever is actually live at commit time (via `update`'s
                // CAS retry), instead of blindly overwriting with `updated`
                // (built from the `current` snapshot taken above) — so a
                // concurrent writer, like the stall ticker below, can't have
                // its change silently discarded if it lands in between.
                _uiState.update { latest ->
                    val merged = when (batch.type) {
                        SensorReadingType.ACCELEROMETER -> latest.copy(
                            receivedBatchCount = latest.receivedBatchCount + 1,
                            accelerometerFeatures = updated.accelerometerFeatures,
                            accelerometerMagnitudeHistory = updated.accelerometerMagnitudeHistory,
                            accelerometerSmoothedHistory = updated.accelerometerSmoothedHistory,
                            isStalled = false
                        )
                        SensorReadingType.GYROSCOPE -> latest.copy(
                            receivedBatchCount = latest.receivedBatchCount + 1,
                            gyroscopeFeatures = updated.gyroscopeFeatures,
                            gyroscopeMagnitudeHistory = updated.gyroscopeMagnitudeHistory,
                            gyroscopeSmoothedHistory = updated.gyroscopeSmoothedHistory,
                            isStalled = false
                        )
                    }
                    merged.copy(motionState = stableMotionState, recordedRowCount = calibrationRecorder.recordedRowCount)
                }
            }
        }

        // No batch-arrival event fires while the transport is stalled, so a
        // separate ticker is needed to notice the silence and surface it.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(STALL_CHECK_INTERVAL_MILLIS)
                val stalled = System.currentTimeMillis() - lastBatchAtMillis > STALL_THRESHOLD_MILLIS
                _uiState.update { latest ->
                    when {
                        stalled == latest.isStalled -> latest
                        stalled -> {
                            // Stale data stopped arriving, so the displayed
                            // state shouldn't keep showing whatever it last
                            // classified — that would read as "still live"
                            // when it isn't.
                            pendingMotionState = null
                            pendingMotionStreak = 0
                            latest.copy(isStalled = true, motionState = MotionState.UNKNOWN)
                        }
                        else -> latest.copy(isStalled = false)
                    }
                }
            }
        }

        // Motion state is otherwise only pushed to the watch on change, which
        // can go long stretches without firing — not frequent enough for the
        // watch to tell "phone app died" from "state just hasn't changed."
        // This periodic resend doubles as a liveness heartbeat: the watch
        // derives its own stall signal from how recently one last arrived.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(MOTION_STATE_HEARTBEAT_MILLIS)
                motionStateSender.send(_uiState.value.motionState)
            }
        }
    }

    // Calibration recording, kept for re-deriving MotionClassifier's
    // thresholds if more labeled data is ever needed again — not wired into
    // the UI right now (see CalibrationControls).
    fun startRecording(label: String) {
        calibrationRecorder.start(label)
        _uiState.update { it.copy(recordingLabel = label, recordedRowCount = 0) }
    }

    fun stopRecording() {
        calibrationRecorder.stopAndFlush(viewModelScope, appContext)
        _uiState.update { it.copy(recordingLabel = null) }
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
        private const val MOTION_STATE_HEARTBEAT_MILLIS = 1000L
    }
}
