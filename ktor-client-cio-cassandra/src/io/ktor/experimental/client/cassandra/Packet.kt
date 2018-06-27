package io.ktor.experimental.client.cassandra

import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import kotlinx.coroutines.experimental.io.*

class Packet(
    val version: Int = 3, val flags: Int = 0, val stream: Int = 0, val opcode: Int,
    val payload: ByteArray
) {
    companion object {
        suspend fun read(reader: ByteReadChannel): Packet {
            val info = reader.readBytesExact(9).openSync()
            val version = info.readU8()
            val flags = info.readU8()
            val stream = info.readU16_be()
            val opcode = info.readU8()
            val length = info.readS32_be()
            val payload = reader.readBytesExact(length)
            return Packet(
                version,
                flags,
                stream,
                opcode,
                payload
            )
        }
    }

    fun toByteArray(): ByteArray = MemorySyncStreamToByteArray {
        write8(version)
        write8(flags)
        write16_be(stream)
        write8(opcode)
        write32_be(payload.size)
        writeBytes(payload)
    }
}