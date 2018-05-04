package com.soywiz.io.ktor.client.mongodb.bson

import com.soywiz.io.ktor.client.mongodb.util.*
import com.soywiz.io.ktor.client.util.*
import kotlinx.io.core.*
import java.io.*
import java.math.*
import java.util.*
import kotlin.collections.LinkedHashSet

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
        // @TODO: Would be nice if BytePacketBuilder allowed to go back for patching
        val payload = buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
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
            is String -> writeBsonElementHead(0x02, name).writeBsonString(obj)
            is Map<*, *> -> writeBsonElementHead(0x03, name).writeBsonDocument(obj as Map<String, Any?>)
            is List<*> ->
                writeBsonElementHead(
                    0x04, name
                ).writeBsonDocument(obj.mapIndexed { index, value -> "$index" to value }.toMap())

            is ByteArray -> writeBsonElementHead(0x05, name).writeBsonBinary(0x00, obj)
            is BsonFunction -> writeBsonElementHead(0x05, name).writeBsonBinary(0x01, obj.data)
            is BsonBinaryOld -> writeBsonElementHead(0x05, name).writeBsonBinary(0x02, obj.data)
            is BsonUuidOld -> writeBsonElementHead(0x05, name).writeBsonBinary(0x03, obj.data)
            is BsonUuid -> writeBsonElementHead(0x05, name).writeBsonBinary(0x04, obj.data)
            is BsonMd5 -> writeBsonElementHead(0x05, name).writeBsonBinary(0x05, obj.data)
            is BsonUserDefined -> writeBsonElementHead(0x05, name).writeBsonBinary(0x80, obj.data)
            is BsonUndefined -> writeBsonElementHead(0x06, name)
            is BsonObjectId -> writeBsonElementHead(0x07, name).writeFully(obj.data, 0, 12)
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
            is BigDecimal -> writeBsonElementHead(0x13, name).writeBsonDecimal128(obj.toDecimal128())
            is BsonDecimal128 -> writeBsonElementHead(0x13, name).writeBsonDecimal128(obj)
            is BsonMinKey -> writeBsonElementHead(0x7F, name)
            is BsonMaxKey -> writeBsonElementHead(0xFF, name)
        }
    }

    fun BytePacketBuilder.writeBsonJavascriptCodeWithScope(code: BsonJavascriptCodeWithScope) {
        val packet = buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
            writeBsonString(code.code)
            writeBsonDocument(code.scope)
        }.readBytes()
        writeInt(packet.size + 4)
        writeFully(packet)
    }

    fun BytePacketBuilder.writeBsonDecimal128(decimal: BsonDecimal128) {
        writeLong(decimal.high)
        writeLong(decimal.low)
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

    fun read(packet: ByteReadPacket): Map<String, Any?> = packet.readBsonDocument()
    fun read(data: ByteArray): Map<String, Any?> = ByteReadPacket(data, ByteOrder.LITTLE_ENDIAN).readBsonDocument()

    fun ByteReadPacket.readBsonDocument(): Map<String, Any?> {
        val dataSize = readInt()
        val out = LinkedHashMap<String, Any?>()
        elements@ while (true) {
            val type = readByte().toInt() and 0xFF
            if (type == 0) break@elements
            val name = readBsonCString()
            out[name] =  when (type) {
                0x01 -> readDouble()
                0x02 -> readBsonString()
                0x03 -> readBsonDocument()
                0x04 -> readBsonDocument().values.toList()
                0x05 -> {
                    val size = readInt()
                    val subtype = readByte().toInt() and 0xFF
                    val data = readBytes(size)
                    when (subtype) {
                        0x00 -> data
                        0x01 -> BsonFunction(data)
                        0x02 -> BsonBinaryOld(data)
                        0x03 -> BsonUuidOld(data)
                        0x04 -> BsonUuid(data)
                        0x05 -> BsonMd5(data)
                        0x80 -> BsonUserDefined(data)
                        else -> data
                    }
                }
                0x06 -> BsonUndefined
                0x07 -> BsonObjectId(readBytes(12))
                0x08 -> readByte().toInt() != 0
                0x09 -> Date(readLong())
                0x0a -> null
                0x0b -> {
                    val pattern = readBsonCString()
                    val flags = readBsonCString()
                    Regex(pattern, LinkedHashSet<RegexOption>().apply {
                        if ('i' in flags) this += RegexOption.IGNORE_CASE
                        if ('m' in flags) this += RegexOption.MULTILINE
                    })
                }
                0x0c -> BsonDbPointerOld(readBytes(12))
                0x0d -> BsonJavascriptCode(readBsonString())
                0x0e -> BsonSymbolOld(readBsonString())
                0x0f -> {
                    val len = readInt()
                    val code = readBsonString()
                    val scope = readBsonDocument()
                    BsonJavascriptCodeWithScope(code, scope)
                }
                0x10 -> readInt()
                0x11 -> BsonTimestamp(readLong())
                0x12 -> readLong()
                0x13 -> readBsonDecimal128()
                0x7F -> BsonMinKey
                0xFF -> BsonMaxKey
                else -> error("Unsupported element of type $type")
            }
        }
        return out
    }

    fun ByteReadPacket.readBsonDecimal128(): BsonDecimal128 {
        val high = readLong()
        val low = readLong()
        return BsonDecimal128(high, low)
    }

    fun ByteReadPacket.readBsonCString(): String {
        val baos = ByteArrayOutputStream()
        while (remaining > 0) {
            val b = readByte().toInt()
            if (b == 0) break
            baos.write(b.toInt())
        }
        return baos.toByteArray().toString(Charsets.UTF_8)
    }

    fun ByteReadPacket.readBsonString(): String {
        val len = readInt()
        val str = readBytes(len)
        return str.sliceArray(0 until str.size - 1).toString(Charsets.UTF_8)
    }
}

object BsonUndefined
object BsonMinKey
object BsonMaxKey
typealias BsonDocument = Map<String, Any?>

class BsonFunction(val data: ByteArray) {
    constructor(data: String) : this(data.toByteArray(Charsets.UTF_8))
}
class BsonBinaryOld(val data: ByteArray)
class BsonUuidOld(val data: ByteArray)
class BsonUuid(val data: ByteArray)
class BsonMd5(val data: ByteArray)
class BsonUserDefined(val data: ByteArray)
class BsonObjectId(val data: ByteArray) {
    init {
        if (data.size != 12) error("BsonObjectId length must be 12")
    }

    override fun toString(): String = "ObjectId(\"${Hex.encodeLower(data)}\")"
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

data class BsonDecimal128(val high: Long, val low: Long) {
}

fun BsonDecimal128.toBigDecimal(): BigDecimal = TODO()
fun BigDecimal.toDecimal128(): BsonDecimal128 = TODO()