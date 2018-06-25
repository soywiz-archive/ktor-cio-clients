package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.postgre.db.*
import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

internal val POSTGRE_ENDIAN = ByteOrder.BIG_ENDIAN

inline fun PostgrePacket(typeChar: Char, callback: ByteArrayOutputStream.() -> Unit): PostgrePacket {
    return PostgrePacket(typeChar, buildByteArray(callback))
}

internal suspend fun ByteReadChannel.readPostgrePacket(config: PostgresConfig): PostgrePacket {
    return _readPostgrePacket(true, config)
}

//private val POSTGRE_ENDIAN = ByteOrder.LITTLE_ENDIAN

fun ByteArray.readInt_be(offset: Int): Int {
    val b0 = this[offset + 0].toInt() and 0xFF
    val b1 = this[offset + 1].toInt() and 0xFF
    val b2 = this[offset + 2].toInt() and 0xFF
    val b3 = this[offset + 3].toInt() and 0xFF
    return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or (b3 shl 0)
}

internal suspend fun ByteReadChannel._readPostgrePacket(readType: Boolean, config: PostgresConfig): PostgrePacket {
    readByteOrder = POSTGRE_ENDIAN

    val type: Char
    val size: Int

    if (config.useByteReadReadInt) {
        // @TODO: Read strange values under pressure
        type = if (readType) readByte().toChar() else '\u0000'
        size = readInt()
    } else {
        // @TODO: Works fine
        val header = ByteArray(if (readType) 5 else 4)
        readFully(header)
        type = if (readType) (header[0].toInt() and 0xFF).toChar() else '\u0000'
        size = if (readType) header.readInt_be(1) else header.readInt_be(0)
    }

    val payloadSize = size - 4
    if (payloadSize < 0) {
        //System.err.println("PG: ERROR: $type")
        throw IllegalStateException("_readPostgrePacket: type=$type, payloadSize=$payloadSize, readType=$readType")
    } else {
        //System.err.println("PG: OK: $type")
    }
    return PostgrePacket(type, readBytesExact(payloadSize))
}

internal suspend fun ByteWriteChannel.writePostgreStartup(
    user: String,
    database: String = user,
    vararg params: Pair<String, String>
) {
    val packet = PostgrePacket('\u0000', buildByteArray {
        write32_be(0x0003_0000) // The protocol version number. The most significant 16 bits are the major version number (3 for the protocol described here). The least significant 16 bits are the minor version number (0 for the protocol described here).
        val pairArray = arrayListOf<Pair<String, String>>().apply {
            add("user" to user)
            add("database" to database)
            add("application_name" to "ktor-cio")
            add("client_encoding" to "UTF8")
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

internal suspend fun ByteWriteChannel._writePostgrePacket(packet: PostgrePacket, first: Boolean) {
    writeByteOrder = POSTGRE_ENDIAN
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.size)
    writeFully(packet.payload)
    flush()
}

internal suspend fun ByteWriteChannel.writePostgrePacket(packet: PostgrePacket) =
    _writePostgrePacket(packet, first = false)

fun String.postgreEscape(): String {
    var out = ""
    for (c in this) {
        when (c) {
            '\u0000' -> out += "\\0"
            '\'' -> out += "\\'"
            '\"' -> out += "\\\""
            '\b' -> out += "\\b"
            '\n' -> out += "\\n"
            '\r' -> out += "\\r"
            '\t' -> out += "\\t"
            '\u0026' -> out += "\\Z"
            '\\' -> out += "\\\\"
            '%' -> out += "\\%"
            '_' -> out += "\\_"
            '`' -> out += "\\`"
            else -> out += c
        }
    }
    return out
}

fun String.postgreQuote(): String = "'${this.postgreEscape()}'"
fun String.postgreTableQuote(): String = "`${this.postgreEscape()}`"