package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.postgre.protocol.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder


@PublishedApi
internal val POSTGRE_ENDIAN = ByteOrder.BIG_ENDIAN

inline fun PostgrePacket(typeChar: Char, callback: BytePacketBuilder.() -> Unit): PostgrePacket {
    val packet = buildPacket(block = callback)
    packet.byteOrder = POSTGRE_ENDIAN
    return PostgrePacket(typeChar, packet)
}

internal suspend fun ByteReadChannel.readPostgrePacket(config: PostgreConfig, readType: Boolean = true): PostgrePacket {
    val type: Char
    val size: Int

    if (config.useByteReadReadInt) {
        // @TODO: Read strange values under pressure
        type = if (readType) readByte().toChar() else '\u0000'
        size = readInt()
    } else {
        // @TODO: Works fine
        val header = readPacket(if (readType) 5 else 4)
        type = if (readType) header.readByte().toChar() else '\u0000'
        size = if (readType) header.readInt() else header.readInt()
    }

    val payloadSize = size - 4
    if (payloadSize < 0) {
        //System.err.println("PG: ERROR: $type")
        throw IllegalStateException("_readPostgrePacket: type=$type, payloadSize=$payloadSize, readType=$readType")
    } else {
        //System.err.println("PG: OK: $type")
    }
    return PostgrePacket(type, readPacket(payloadSize))
}

internal suspend fun ByteWriteChannel.writePostgreStartup(
    user: String,
    database: String = user,
    vararg params: Pair<String, String>
) {
    val packet = PostgrePacket('\u0000') {
        /**
         * The protocol version number.
         * The most significant 16 bits are the major version number (3 for the protocol described here).
         * The least significant 16 bits are the minor version number (0 for the protocol described here).
         */
        writeInt(0x0003_0000)
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
    }

    writePostgrePacket(packet, first = true)
}

internal suspend fun ByteWriteChannel.writePostgrePacket(packet: PostgrePacket, first: Boolean = false) {
    writeByteOrder = POSTGRE_ENDIAN
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.remaining)
    writePacket(packet.payload)
    flush()
}

internal fun String.postgreEscape(): String {
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

internal fun Output.writeStringz(value: String, charset: Charset = Charsets.UTF_8) {
    val data = value.toByteArray(charset)
    writeFully(data)
    writeByte(0)
}

internal fun Input.readStringz(charset: Charset = Charsets.UTF_8): String = buildPacket {
    readUntilDelimiter(0, this)
}.readText(charset)

internal fun String.postgreQuote(): String = "'${this.postgreEscape()}'"
internal fun String.postgreTableQuote(): String = "`${this.postgreEscape()}`"