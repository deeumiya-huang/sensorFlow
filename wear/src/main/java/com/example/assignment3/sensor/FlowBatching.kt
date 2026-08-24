package com.example.assignment3.sensor

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Buffers items and emits them as a batch every [windowMillis], dropping empty windows. */
fun <T> Flow<T>.chunkedByTime(windowMillis: Long): Flow<List<T>> = channelFlow {
    val buffer = mutableListOf<T>()
    val mutex = Mutex()

    val collectJob = launch {
        collect { item -> mutex.withLock { buffer.add(item) } }
    }
    val tickerJob = launch {
        while (isActive) {
            delay(windowMillis)
            val batch = mutex.withLock {
                if (buffer.isEmpty()) null else buffer.toList().also { buffer.clear() }
            }
            if (batch != null) send(batch)
        }
    }

    awaitClose {
        collectJob.cancel()
        tickerJob.cancel()
    }
}
