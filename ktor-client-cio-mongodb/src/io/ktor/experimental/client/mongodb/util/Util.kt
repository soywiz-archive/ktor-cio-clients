package io.ktor.experimental.client.mongodb.util

import io.ktor.experimental.client.mongodb.bson.*
import kotlinx.io.core.*

fun ByteReadPacket(data: ByteArray, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): ByteReadPacket =
    buildPacket(byteOrder = byteOrder) {
        this.byteOrder = byteOrder
        writeFully(data)
    }

inline fun buildPacket(
    headerSizeHint: Int = 0,
    byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
    block: BytePacketBuilder.() -> Unit
): ByteReadPacket {
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

fun ByteArray.asReadPacket(byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN) =
    ByteReadPacket(this, byteOrder)

fun BytePacketBuilder.writePacketWithIntLength(
    includeLength: Boolean = true,
    increment: Int = 0,
    block: BytePacketBuilder.() -> Unit
) {
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

fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V>): Map<K, V> {
    val out = LinkedHashMap<K, V>()
    for ((k, v) in pairs) if (v != null) out[k] = v
    return out
}

fun <K, V> MutableMap<K, V>.putNotNull(key: K, value: V) {
    if (value != null) put(key, value)
}

inline fun mongoMap(callback: MutableMap<String, Any?>.() -> Unit): BsonDocument =
    LinkedHashMap<String, Any?>().apply(callback)

