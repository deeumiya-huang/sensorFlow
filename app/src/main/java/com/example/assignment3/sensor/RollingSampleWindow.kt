package com.example.assignment3.sensor

import com.example.assignment3.common.SensorSample
import kotlin.math.sqrt

/**
 * Keeps a rolling window of the last [windowNanos] worth of samples (by each
 * sample's own sensor timestamp, not arrival time). Feed it each incoming
 * batch via [addAll]; it returns the full current window's contents.
 *
 * [maxPlausibleMagnitude] discards samples whose vector magnitude exceeds a
 * physically-impossible-for-a-wrist value (hardware glitch / corrupted
 * transmission), not a statistical outlier filter — a real tap's sharp spike
 * must survive untouched, since that spike is the signal we're trying to
 * detect later.
 */
class RollingSampleWindow(
    private val windowNanos: Long,
    private val maxPlausibleMagnitude: Float = Float.MAX_VALUE
) {

    private val samples = ArrayDeque<SensorSample>()

    fun addAll(newSamples: List<SensorSample>): List<SensorSample> {
        for (sample in newSamples) {
            val magnitude = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
            if (magnitude.isFinite() && magnitude <= maxPlausibleMagnitude) {
                samples.addLast(sample)
            }
        }
        val newestTimestamp = samples.lastOrNull()?.timestampNanos ?: return emptyList()
        val cutoff = newestTimestamp - windowNanos
        while (samples.isNotEmpty() && samples.first().timestampNanos < cutoff) {
            samples.removeFirst()
        }
        return samples
    }
}
