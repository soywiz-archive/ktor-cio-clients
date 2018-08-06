package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.postgre.protocol.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder


inline fun PostgrePacket(typeChar: Char, callback: BytePacketBuilder.() -> Unit): PostgrePacket =
    PostgrePacket(typeChar, buildPacket { callback() })

internal suspend fun ByteReadChannel.readPostgrePacket(startUp: Boolean = false): PostgrePacket {
    val type = if (!startUp) readByte().toChar() else '\u0000'
    val payloadSize = readInt() - 4
    if (payloadSize < 0) throw IllegalStateException("readPostgrePacket: type=$type, payloadSize=$payloadSize")
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
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.remaining)
    writePacket(packet.payload)
    flush()
}

internal suspend fun ByteWriteChannel.writePostgrePacket(
    type: MessageType,
    first: Boolean = false,
    block: BytePacketBuilder.() -> Unit
) {
    writePostgrePacket(PostgrePacket(type.code, block), first)
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
