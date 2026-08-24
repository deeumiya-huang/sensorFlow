package com.example.assignment3.sensor

import android.content.Context
import android.util.Log
import com.example.assignment3.common.DataLayerPaths
import com.example.assignment3.common.SensorBatchCodec
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class SensorDataSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun send(type: SensorReadingType, samples: List<SensorSample>) {
        if (samples.isEmpty()) return
        val path = DataLayerPaths.pathFor(type)
        val payload = SensorBatchCodec.encode(samples)
        try {
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send $path batch to phone", e)
        }
    }

    private companion object {
        const val TAG = "SensorDataSender"
    }
}
