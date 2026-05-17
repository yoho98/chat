package com.sokind.chat.infrastructure.jpa

import java.nio.ByteBuffer
import java.util.UUID

object UuidBytes {

    fun toBytes(uuid: UUID): ByteArray {
        val byteBuffer = ByteBuffer.wrap(ByteArray(16))
        byteBuffer.putLong(uuid.mostSignificantBits)
        byteBuffer.putLong(uuid.leastSignificantBits)
        return byteBuffer.array()
    }

    fun fromBytes(bytes: ByteArray): UUID {
        val byteBuffer = ByteBuffer.wrap(bytes)
        val high = byteBuffer.long
        val low = byteBuffer.long
        return UUID(high, low)
    }
}
