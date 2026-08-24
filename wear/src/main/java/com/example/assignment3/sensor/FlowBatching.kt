package com.example.assignment3.sensor

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Buffers items and emits them as a batch every [windowMillis], dropping empty
 * windows. The buffer is capped at [maxBufferedItems]: if a batch can't be sent
 * (e.g. Bluetooth drops) for longer than that many items' worth of time, the
 * oldest items are evicted instead of growing unbounded. For a live sensor
 * stream, stale readings are useless anyway once reconnected — better to drop
 * them than to OOM or replay a long backlog with growing latency.
 */
fun <T> Flow<T>.chunkedByTime(
    windowMillis: Long,
    maxBufferedItems: Int = 500
): Flow<List<T>> = channelFlow {
    val buffer = ArrayDeque<T>()
    val mutex = Mutex()

    val collectJob = launch {
        collect { item ->
            mutex.withLock {
                buffer.addLast(item)
                while (buffer.size > maxBufferedItems) {
                    buffer.removeFirst()
                }
            }
        }
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
