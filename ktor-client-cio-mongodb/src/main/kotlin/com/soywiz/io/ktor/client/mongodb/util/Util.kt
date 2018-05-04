package com.soywiz.io.ktor.client.mongodb.util

import kotlinx.io.core.*

fun ByteReadPacket(data: ByteArray, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): ByteReadPacket = buildPacket(byteOrder = byteOrder) {
    this.byteOrder = byteOrder
    writeFully(data)
}

inline fun buildPacket(headerSizeHint: Int = 0, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN, block: BytePacketBuilder.() -> Unit): ByteReadPacket {
    val builder = BytePacketBuilder(headerSizeHint)
    builder.byteOrder = byteOrder
    try {
        block(builder)
        return builder.build()
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}
