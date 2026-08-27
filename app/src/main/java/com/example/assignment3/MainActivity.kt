package com.example.assignment3

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.assignment3.sensor.PhoneSensorUiState
import com.example.assignment3.sensor.SensorReceiveRepository
import com.example.assignment3.sensor.SensorViewModel
import com.example.assignment3.ui.MotionPixelArt
import com.example.assignment3.ui.theme.Assignment3Theme

private val MacaronMint = Color(0xFFB8F2E6)
private val MacaronPink = Color(0xFFFFD3E0)
private val BackgroundGradient = Brush.verticalGradient(listOf(MacaronMint, MacaronPink))
private val ChartWindowSeconds = SensorViewModel.WINDOW_NANOS / 1_000_000_000f

// A rounded system font (present on most Android devices, e.g. Pixel) reads
// friendlier than the default sans-serif while staying just as legible;
// falls back to the platform default automatically if a device lacks it.
private val CuteFontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-rounded")))

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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundGradient)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            StatusColumn(uiState)
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.6f)
            )
            ChartsColumn(uiState = uiState, showSpacer = false, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundGradient)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusColumn(uiState, modifier = Modifier.align(Alignment.CenterHorizontally))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.6f)
            )
            ChartsColumn(uiState = uiState, modifier = Modifier.fillMaxWidth().weight(0.75f))
        }
    }
}

@Composable
private fun StatusColumn(uiState: PhoneSensorUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MotionPixelArt(motionState = uiState.motionState)
        Crossfade(targetState = uiState.motionState, animationSpec = tween(300), label = "motionState") { state ->
            Text(
                text = state.name,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = CuteFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4E342E)
            )
        }
        if (uiState.isStalled) {
            Text(text = "Receiving data...")
        }
    }
}

@Composable
private fun ChartsColumn(uiState: PhoneSensorUiState, showSpacer: Boolean = true, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Accelerometer (m/s²) · last %.1fs".format(ChartWindowSeconds),
                fontFamily = CuteFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D6B)
            )
            MagnitudeChart(
                rawValues = uiState.accelerometerMagnitudeHistory,
                smoothedValues = uiState.accelerometerSmoothedHistory,
                lineColor = Color(0xFF2E7D6B),
                fixedRange = 0f..45f,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }

        if (showSpacer) {
            Spacer(modifier = Modifier.weight(0.05f))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Gyroscope (rad/s) · last %.1fs".format(ChartWindowSeconds),
                fontFamily = CuteFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC2477A)
            )
            MagnitudeChart(
                rawValues = uiState.gyroscopeMagnitudeHistory,
                smoothedValues = uiState.gyroscopeSmoothedHistory,
                lineColor = Color(0xFFC2477A),
                fixedRange = 0f..16f,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
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
    val axisLabelWidth = 26.dp
    Box(modifier = modifier.background(Color.White.copy(alpha = 0.35f))) {
        // Min/mid/max labels give the waveform an actual scale to read
        // against, rather than just a shape — standard for this kind of
        // live sensor strip chart.
        Column(
            modifier = Modifier.fillMaxHeight().width(axisLabelWidth).padding(vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "%.0f".format(fixedRange.endInclusive), fontFamily = CuteFontFamily, fontSize = 10.sp, color = Color(0xFF5B5B5B))
            Text(text = "%.0f".format((fixedRange.start + fixedRange.endInclusive) / 2f), fontFamily = CuteFontFamily, fontSize = 10.sp, color = Color(0xFF5B5B5B))
            Text(text = "%.0f".format(fixedRange.start), fontFamily = CuteFontFamily, fontSize = 10.sp, color = Color(0xFF5B5B5B))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = axisLabelWidth)
        ) {
            if (rawValues.size < 2) return@Canvas

            val rangeStart = fixedRange.start
            val rangeSpan = fixedRange.endInclusive - fixedRange.start
            val gridColor = Color.Black.copy(alpha = 0.12f)

            for (fraction in listOf(0f, 0.5f, 1f)) {
                val y = size.height - fraction * size.height
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

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
}
