package com.example.assignment3.common

import java.nio.ByteBuffer

/**
 * Wire format shared by watch (encode) and phone (decode): a 4-byte sample
 * count, followed by that many fixed-size records of [x, y, z, timestampNanos]
 * (float, float, float, long). Both sides must stay in sync with this layout.
 */
object SensorBatchCodec {
    private const val BYTE_FOR_HEADER = 4
    private const val BYTES_PER_SAMPLE = 4 + 4 + 4 + 8

    fun encode(samples: List<SensorSample>): ByteArray {
        val buffer = ByteBuffer.allocate( BYTE_FOR_HEADER + samples.size * BYTES_PER_SAMPLE)
        // put the sample size into the 4 bytes of header
        buffer.putInt(samples.size)
        // put all samples into buffer
        for (sample in samples) {
            buffer.putFloat(sample.x)
            buffer.putFloat(sample.y)
            buffer.putFloat(sample.z)
            buffer.putLong(sample.timestampNanos)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<SensorSample> {
        val buffer = ByteBuffer.wrap(bytes)
        val count = buffer.int
        return List(count) {
            SensorSample(
                x = buffer.float,
                y = buffer.float,
                z = buffer.float,
                timestampNanos = buffer.long
            )
        }
    }
}
