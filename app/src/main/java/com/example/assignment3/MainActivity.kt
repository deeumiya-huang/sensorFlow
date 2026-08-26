package com.example.assignment3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.assignment3.sensor.SensorReceiveRepository
import com.example.assignment3.sensor.SensorViewModel
import com.example.assignment3.ui.MotionPixelArt
import com.example.assignment3.ui.theme.Assignment3Theme

private val MacaronMint = Color(0xFFB8F2E6)
private val MacaronPink = Color(0xFFFFD3E0)
private val BackgroundGradient = Brush.verticalGradient(listOf(MacaronMint, MacaronPink))
private val ChartWindowSeconds = SensorViewModel.WINDOW_NANOS / 1_000_000_000f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Assignment3Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    SensorScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SensorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: SensorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SensorViewModel(SensorReceiveRepository(context), context.applicationContext) }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MotionPixelArt(
            motionState = uiState.motionState,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = uiState.motionState.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (uiState.isStalled) {
            Text(
                text = "Receiving data...",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "Calibration recording")
        val recordingLabel = uiState.recordingLabel
        if (recordingLabel == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (label in listOf("STATIC", "TAP", "SHAKE", "WALK")) {
                    Button(onClick = { viewModel.startRecording(label) }) {
                        Text(text = label)
                    }
                }
            }
        } else {
            Text(text = "Recording $recordingLabel... (${uiState.recordedRowCount} rows)")
            Button(onClick = { viewModel.stopRecording() }) {
                Text(text = "Stop")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "Accelerometer (m/s²) · last %.1fs".format(ChartWindowSeconds), fontWeight = FontWeight.Bold)
        MagnitudeChart(
            rawValues = uiState.accelerometerMagnitudeHistory,
            smoothedValues = uiState.accelerometerSmoothedHistory,
            lineColor = Color(0xFF2E7D6B),
            fixedRange = 0f..45f
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "Gyroscope (rad/s) · last %.1fs".format(ChartWindowSeconds), fontWeight = FontWeight.Bold)
        MagnitudeChart(
            rawValues = uiState.gyroscopeMagnitudeHistory,
            smoothedValues = uiState.gyroscopeSmoothedHistory,
            lineColor = Color(0xFFC2477A),
            fixedRange = 0f..16f
        )
    }
}

/**
 * [fixedRange] is deliberately NOT auto-scaled to the current window's
 * min/max: auto-scaling stretches whatever noise is present to fill the
 * whole chart height, which makes static's tiny jitter look just as
 * "spiky" as an actual shake. A fixed range lets amplitude differences
 * between states actually show up visually.
 */
@Composable
private fun MagnitudeChart(
    rawValues: List<Float>,
    smoothedValues: List<Float>,
    lineColor: Color,
    fixedRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.White.copy(alpha = 0.35f))
    ) {
        if (rawValues.size < 2) return@Canvas

        val rangeStart = fixedRange.start
        val rangeSpan = fixedRange.endInclusive - fixedRange.start

        fun pathFor(values: List<Float>): Path {
            val stepX = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val normalized = ((value - rangeStart) / rangeSpan).coerceIn(0f, 1f)
                val y = size.height - (normalized * size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        // Raw signal drawn faint underneath; smoothed signal bold on top —
        // the smoothed line is what the classifier actually reasons about.
        drawPath(path = pathFor(rawValues), color = lineColor.copy(alpha = 0.35f), style = Stroke(width = 2f))
        if (smoothedValues.size >= 2) {
            drawPath(path = pathFor(smoothedValues), color = lineColor, style = Stroke(width = 4f))
        }
    }
}
