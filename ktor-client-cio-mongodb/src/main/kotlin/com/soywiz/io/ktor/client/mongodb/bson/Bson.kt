package com.soywiz.io.ktor.client.mongodb.bson

import kotlinx.io.core.*
import java.math.*
import java.util.*

/**
 * http://bsonspec.org/
 */
object Bson {
    fun build(obj: Map<String, Any?>): ByteArray = buildPacket { writeBsonDocument(obj) }.readBytes()

    fun writeDocument(obj: Map<String, Any?>, out: BytePacketBuilder) {
        out.writeBsonDocument(obj)
    }

    fun BytePacketBuilder.writeBsonDocument(obj: Map<String, Any?>) {
        byteOrder = ByteOrder.LITTLE_ENDIAN
        val payload = buildPacket {
            byteOrder = ByteOrder.LITTLE_ENDIAN
            for ((key, value) in obj) {
                this.writeBsonElement(key, value)
            }
        }.readBytes()
        writeInt(payload.size + 4 + 1)
        writeFully(payload)
        writeByte(0)
    }

    fun BytePacketBuilder.writeBsonElement(name: String, obj: Any?) {
        when (obj) {
            is Double -> writeBsonElementHead(0x01, name).writeDouble(obj)
            is String -> run { writeBsonElementHead(0x02, name).writeBsonString(obj) }
            is Map<*, *> -> run { writeBsonElementHead(0x03, name).writeBsonDocument(obj as Map<String, Any?>) }
            is List<*> -> run {
                writeBsonElementHead(
                    0x04,
                    name
                ).writeBsonDocument(obj.mapIndexed { index, value -> "$index" to value }.toMap())
            }
            is ByteArray -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x00, obj) }
            is BsonFunction -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x01, obj.data) }
            is BsonBinaryOld -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x02, obj.data) }
            is BsonUuidOld -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x03, obj.data) }
            is BsonUuid -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x04, obj.data) }
            is BsonMd5 -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x05, obj.data) }
            is BsonUserDefined -> run { writeBsonElementHead(0x05, name).writeBsonBinary(0x80, obj.data) }
            is BsonUndefined -> run { writeBsonElementHead(0x06, name) }
            is BsonObjectId -> run { writeBsonElementHead(0x07, name).writeFully(obj.data, 0, 12) }
            is Boolean -> writeBsonElementHead(0x08, name).writeByte(if (obj) 1 else 0)
            is Date -> writeBsonElementHead(0x09, name).writeLong(obj.time)
            null -> writeBsonElementHead(0x0A, name)
            is Regex -> writeBsonElementHead(0x0B, name).writeBsonCString(obj.pattern).writeBsonCString(
                (if (RegexOption.IGNORE_CASE in obj.options) "i" else "") +
                        (if (RegexOption.MULTILINE in obj.options) "m" else "")
            )
            is BsonDbPointerOld -> writeBsonElementHead(0x0C, name).writeFully(obj.data, 0, 12)
            is BsonJavascriptCode -> writeBsonElementHead(0x0D, name).writeBsonString(obj.code)
            is BsonSymbolOld -> writeBsonElementHead(0x0E, name).writeBsonString(obj.symbol)
            is BsonJavascriptCodeWithScope -> writeBsonElementHead(0x0F, name).writeBsonJavascriptCodeWithScope(obj)
            is Int -> writeBsonElementHead(0x10, name).writeInt(obj)
            is BsonTimestamp -> writeBsonElementHead(0x11, name).writeLong(obj.timestamp)
            is Long -> writeBsonElementHead(0x12, name).writeLong(obj)
            is BigDecimal -> writeBsonElementHead(0x13, name).writeBsonDecimal128(obj)
            is BsonMinKey -> writeBsonElementHead(0x7F, name)
            is BsonMaxKey -> writeBsonElementHead(0xFF, name)
        }
    }

    fun BytePacketBuilder.writeBsonJavascriptCodeWithScope(code: BsonJavascriptCodeWithScope) {
        TODO("Not supported Bson JavascriptCode With Scope yet")
    }

    fun BytePacketBuilder.writeBsonDecimal128(decimal: BigDecimal) {
        writeLong(0)
        writeLong(0)
        TODO("Not supported Bson decimal128 yet")
    }

    fun BytePacketBuilder.writeBsonElementHead(type: Int, name: String) = this.apply {
        writeByte(type.toByte())
        writeBsonCString(name)
    }

    fun BytePacketBuilder.writeBsonBinary(type: Int, obj: ByteArray) = this.apply {
        writeInt(obj.size)
        writeByte(type.toByte())
        writeFully(obj)
    }

    fun BytePacketBuilder.writeBsonString(str: String) = this.apply {
        val bytes = str.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size + 1)
        writeFully(bytes)
        writeByte(0)
    }

    fun BytePacketBuilder.writeBsonCString(str: String) = this.apply {
        writeStringUtf8(str)
        writeByte(0)
    }

    fun read(packet: ByteReadPacket): Any? {
        TODO()
    }

    fun ByteReadPacket.readElement() {
        TODO()
    }
}

object BsonUndefined
object BsonMinKey
object BsonMaxKey
class BsonDocument
class BsonFunction(val data: ByteArray)
class BsonBinaryOld(val data: ByteArray)
class BsonUuidOld(val data: ByteArray)
class BsonUuid(val data: ByteArray)
class BsonMd5(val data: ByteArray)
class BsonUserDefined(val data: ByteArray)
class BsonObjectId(val data: ByteArray) {
    init {
        if (data.size != 12) error("BsonObjectId length must be 12")
    }
}

class BsonDbPointerOld(val data: ByteArray) {
    init {
        if (data.size != 12) error("BsonDbPointerOld length must be 12")
    }
}

class BsonJavascriptCode(val code: String) {
}

class BsonJavascriptCodeWithScope(val code: String, val scope: Map<String, Any?>) {
}

class BsonSymbolOld(val symbol: String)
class BsonTimestamp(val timestamp: Long)
