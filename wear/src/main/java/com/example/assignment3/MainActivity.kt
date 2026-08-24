package com.example.assignment3

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.assignment3.sensor.SensorReading
import com.example.assignment3.sensor.SensorRepository
import com.example.assignment3.sensor.SensorViewModel
import com.example.assignment3.theme.Assignment3WearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            Assignment3WearTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val viewModel: SensorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SensorViewModel(SensorRepository(context)) }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Accel", color = MaterialTheme.colors.primary)
        Text(text = uiState.accelerometer.axisText())
        Text(text = "Gyro", color = MaterialTheme.colors.primary)
        Text(text = uiState.gyroscope.axisText())
    }
}

private fun SensorReading?.axisText(): String =
    if (this == null) "--" else "%.2f, %.2f, %.2f".format(x, y, z)
