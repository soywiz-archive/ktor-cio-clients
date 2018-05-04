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

fun ByteReadPacket.withByteOrder(byteOrder: ByteOrder): ByteReadPacket {
    return buildPacket {
        this.byteOrder = byteOrder
        writeFully(readBytes())
    }
}

fun ByteArray.asReadPacket(byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN) = ByteReadPacket(this, byteOrder)

fun BytePacketBuilder.writePacketWithIntLength(includeLength: Boolean = true, increment: Int = 0, block: BytePacketBuilder.() -> Unit) {
    val bytes = buildPacket {
        this.byteOrder = this@writePacketWithIntLength.byteOrder
        this.block()
    }.readBytes()
    writeInt(bytes.size + increment + (if (includeLength) 4 else 0))
    writeFully(bytes)
}

fun ByteReadPacket.readPacket(count: Int = remaining, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteReadPacket {
    return readBytes(count).asReadPacket(order)
}