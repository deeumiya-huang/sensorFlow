package com.example.assignment3.sensor

import android.content.Context
import android.util.Log
import com.example.assignment3.common.DataLayerPaths
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Fire-and-forget push of the classified motion state to the watch, so its
 * small screen can show what the phone decided instead of raw sensor
 * numbers. Only sent on change (see [SensorViewModel]), so this mirrors
 * :wear's SensorDataSender's node-caching approach without needing to share
 * code across modules for one tiny one-way message.
 */
class MotionStateSender(context: Context) {

    // Narrowed to applicationContext since these are retained for the whole
    // ViewModel lifetime — an Activity context here would leak the Activity.
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    private var cachedNodes: List<Node> = emptyList()
    private var cachedAtMillis = 0L

    suspend fun send(state: MotionState) {
        val payload = state.name.toByteArray(Charsets.UTF_8)
        try {
            for (node in connectedNodes()) {
                messageClient.sendMessage(node.id, DataLayerPaths.MOTION_STATE, payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send motion state to watch", e)
            cachedNodes = emptyList()
        }
    }

    private suspend fun connectedNodes(): List<Node> {
        val now = System.currentTimeMillis()
        if (cachedNodes.isEmpty() || now - cachedAtMillis > NODE_CACHE_TTL_MILLIS) {
            cachedNodes = nodeClient.connectedNodes.await()
            cachedAtMillis = now
        }
        return cachedNodes
    }

    private companion object {
        const val TAG = "MotionStateSender"
        const val NODE_CACHE_TTL_MILLIS = 10_000L
    }
}
