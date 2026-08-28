package com.example.assignment3.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * STATIC/TAP/SHAKE/WALK calibration-recording controls. Not currently wired
 * into the main screen — kept ready to reuse if more calibration is ever
 * needed, see [CalibrationLogger].
 */
@Composable
fun CalibrationControls(
    recordingLabel: String?,
    recordedRowCount: Int,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recordingLabel == null) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (label in listOf("STATIC", "TAP", "SHAKE", "WALK")) {
                Button(onClick = { onStartRecording(label) }) {
                    Text(text = label)
                }
            }
        }
    } else {
        Column(modifier = modifier) {
            Text(text = "Recording $recordingLabel... ($recordedRowCount rows)")
            Button(onClick = onStopRecording) {
                Text(text = "Stop")
            }
        }
    }
}
