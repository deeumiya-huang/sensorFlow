package com.example.assignment3.sensor

import android.content.Context
import android.util.Log
import com.example.assignment3.common.DataLayerPaths
import com.example.assignment3.common.SensorBatchCodec
import com.example.assignment3.common.SensorReadingType
import com.example.assignment3.common.SensorSample
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class SensorDataSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private var cachedNodes: List<Node> = emptyList()
    private var cachedAtMillis = 0L

    suspend fun send(type: SensorReadingType, samples: List<SensorSample>) {
        if (samples.isEmpty()) return
        val path = DataLayerPaths.pathFor(type)
        val payload = SensorBatchCodec.encode(samples)
        try {
            for (node in connectedNodes()) {
                messageClient.sendMessage(node.id, path, payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send $path batch to phone", e)
            cachedNodes = emptyList() // force a fresh lookup next time, in case the node list changed
        }
    }

    // Both sensor pipelines share this instance, so caching here avoids two
    // redundant Binder calls to Play Services every ~500ms just to ask
    // "who's connected" when that almost never changes mid-session.
    private suspend fun connectedNodes(): List<Node> {
        val now = System.currentTimeMillis()
        if (cachedNodes.isEmpty() || now - cachedAtMillis > NODE_CACHE_TTL_MILLIS) {
            cachedNodes = nodeClient.connectedNodes.await()
            cachedAtMillis = now
        }
        return cachedNodes
    }

    private companion object {
        const val TAG = "SensorDataSender"
        const val NODE_CACHE_TTL_MILLIS = 10_000L
    }
}
