package com.example.assignment3.sensor

import android.content.Context
import com.example.assignment3.common.DataLayerPaths
import com.example.assignment3.common.SensorBatchCodec
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class SensorBatch(
    val type: SensorReadingType,
    val samples: List<SensorSample>
)

class SensorReceiveRepository(context: Context) {

    private val messageClient = Wearable.getMessageClient(context.applicationContext)

    val sensorBatches: Flow<SensorBatch> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            val type = DataLayerPaths.typeForPath(event.path) ?: return@OnMessageReceivedListener
            trySend(SensorBatch(type, SensorBatchCodec.decode(event.data)))
        }
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
}
