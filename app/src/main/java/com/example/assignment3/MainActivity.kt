package com.example.assignment3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.assignment3.sensor.SensorFeatures
import com.example.assignment3.sensor.SensorReceiveRepository
import com.example.assignment3.sensor.SensorViewModel
import com.example.assignment3.ui.theme.Assignment3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Assignment3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
            initializer { SensorViewModel(SensorReceiveRepository(context)) }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Batches received: ${uiState.receivedBatchCount}")

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "Accelerometer")
        FeatureList(uiState.accelerometerFeatures)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "Gyroscope")
        FeatureList(uiState.gyroscopeFeatures)
    }
}

@Composable
private fun FeatureList(features: SensorFeatures?) {
    if (features == null) {
        Text(text = "-- no data yet --")
        return
    }
    Text(text = "samples: ${features.sampleCount}")
    Text(text = "mean: %.3f".format(features.mean))
    Text(text = "stdDev: %.3f".format(features.stdDev))
    Text(text = "peakToPeak: %.3f".format(features.peakToPeak))
    Text(text = "zeroCrossing: ${features.zeroCrossingCount}")
    Text(text = "energy: %.3f".format(features.energy))
    Text(text = "maxJerk: %.3f".format(features.maxJerk))
}
