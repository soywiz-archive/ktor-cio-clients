package com.soywiz.io.ktor.client.postgre

import com.soywiz.io.ktor.client.util.*
import com.soywiz.io.ktor.client.util.sync.*
import kotlinx.coroutines.experimental.io.*

// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html

class PostgrePacket(val type: Int, val payload: ByteArray) {
}

private suspend fun ByteReadChannel.readPostgrePacket(): PostgrePacket {
    readByteOrder = ByteOrder.LITTLE_ENDIAN
    val type = readByte().toInt() and 0xFF
    val size = readInt()
    return PostgrePacket(type, readBytesExact(size - 4))
}

private suspend fun ByteWriteChannel.writePostgreStartup() {
    val packet = PostgrePacket(0, buildByteArray {
        writeInt(0x0003_0000) // The protocol version number. The most significant 16 bits are the major version number (3 for the protocol described here). The least significant 16 bits are the minor version number (0 for the protocol described here).

    })
    _writePostgrePacket(packet, first = true)
}

private suspend fun ByteWriteChannel._writePostgrePacket(packet: PostgrePacket, first: Boolean) {
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.size)
    writeFully(packet.payload)
}

private suspend fun ByteWriteChannel.writePostgrePacket(packet: PostgrePacket) = _writePostgrePacket(packet, first = false)
