package com.offlinestudy.solver.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stores a FloatArray embedding as a compact byte blob instead of JSON/text. */
class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buffer.float
        return out
    }
}
