package com.example.assignment3.sensor

import android.content.Context
import com.example.assignment3.common.DataLayerPaths
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Mirrors :app's SensorReceiveRepository, one path in the opposite direction. */
class MotionStateReceiveRepository(context: Context) {

    private val messageClient = Wearable.getMessageClient(context.applicationContext)

    val motionStates: Flow<WatchMotionState> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != DataLayerPaths.MOTION_STATE) return@OnMessageReceivedListener
            val name = String(event.data, Charsets.UTF_8)
            trySend(runCatching { WatchMotionState.valueOf(name) }.getOrDefault(WatchMotionState.UNKNOWN))
        }
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
}
