package io.ktor.experimental.client.postgre.protocol

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.postgre.*
import io.ktor.experimental.client.postgre.scheme.*
import kotlinx.io.core.*

fun ByteReadPacket.readException(): PostgreException {
    val details = mutableListOf<Pair<Char, String>>()

    while (remaining > 0) {
        val type = readByte()

        if (type == 0.toByte()) {
            check(remaining == 0L) { "There are some remaining bytes in exception message: $remaining" }
            break
        }

        val message = readCString()
        details += type.toChar() to message
    }

    return PostgreException(details)
}

fun ByteReadPacket.readColumns(): List<DBColumn> {
    val count = readShort().toInt() and 0xffff
    val result = mutableListOf<DBColumn>()

    for (index in 0 until count) {
        result += PostgreColumn(
            index = index,
            name = readCString(),
            tableOID = readInt(),
            columnIndex = readShort().toInt() and 0xffff,
            typeOID = readInt(),
            typeSize = readShort().toInt() and 0xffff,
            typeMod = readInt(),
            text = readShort().toInt() == 0
        )
    }

    return result
}

fun ByteReadPacket.readRow(): DBRow {
    val count = readShort().toInt() and 0xffff
    return DBRow(columns, (0 until count).map {
        val packetSize = readInt()
        readBytes(packetSize)
    })
}
