package com.soywiz.io.ktor.client.postgre

import com.soywiz.io.ktor.client.util.*
import com.soywiz.io.ktor.client.util.sync.*
import kotlinx.coroutines.experimental.io.*

// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html

class PostgrePacket(val type: Int, val payload: ByteArray) {
}

internal suspend fun ByteReadChannel.readPostgrePacket(): PostgrePacket {
    return _readPostgrePacket(readType = true)
}

internal suspend fun ByteReadChannel._readPostgrePacket(readType: Boolean): PostgrePacket {
    readByteOrder = ByteOrder.LITTLE_ENDIAN
    val type = if (readType) readByte().toInt() and 0xFF else 0
    val size = readInt()
    return PostgrePacket(type, readBytesExact(size - 4))
}

internal suspend fun ByteWriteChannel.writePostgreStartup(user: String, database: String? = null, vararg params: Pair<String, String>) {
    val packet = PostgrePacket(0, buildByteArray {
        write32_le(0x0003_0000) // The protocol version number. The most significant 16 bits are the major version number (3 for the protocol described here). The least significant 16 bits are the minor version number (0 for the protocol described here).
        val pairArray = arrayListOf<Pair<String, String>>().apply {
            add("user" to user)
            if (database != null) add("database" to database)
            addAll(params)
        }
        for ((key, value) in pairArray) {
            writeStringz(key)
            writeStringz(value)
        }
        writeStringz("")
    })
    _writePostgrePacket(packet, first = true)
}

private suspend fun ByteWriteChannel._writePostgrePacket(packet: PostgrePacket, first: Boolean) {
    writeByteOrder = ByteOrder.LITTLE_ENDIAN
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.size)
    writeFully(packet.payload)
    flush()
}

internal suspend fun ByteWriteChannel.writePostgrePacket(packet: PostgrePacket) = _writePostgrePacket(packet, first = false)
