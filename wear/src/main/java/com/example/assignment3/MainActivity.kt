package com.example.assignment3

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.assignment3.sensor.SensorUiState
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
            initializer { SensorViewModel(context.applicationContext) }
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
        Text(text = uiState.motionState.name, style = MaterialTheme.typography.title2)
        ConnectionLight(uiState = uiState, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ConnectionLight(uiState: SensorUiState, modifier: Modifier = Modifier) {
    val dotColor = if (uiState.isConnectionStalled) Color(0xFFE05D5D) else Color(0xFF4CAF50)
    val label = if (uiState.isConnectionStalled) "Connection lost" else "Collecting"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(text = label, style = MaterialTheme.typography.caption2)
    }
}
