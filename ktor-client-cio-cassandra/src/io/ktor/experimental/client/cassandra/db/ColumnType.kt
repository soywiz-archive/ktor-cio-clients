package io.ktor.experimental.client.cassandra.db

import io.ktor.experimental.client.cassandra.*
import io.ktor.experimental.client.util.sync.*
import java.io.*
import java.nio.*

interface ColumnType<T> {
    fun interpret(data: ByteArray): T

    data class CUSTOM(val value: String) : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object ASCII : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object BIGINT : ColumnType<Long> {
        override fun interpret(data: ByteArray) = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getLong(0)
    }

    object BLOB : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object BOOLEAN : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object COUNTER : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object DECIMAL : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object DOUBLE : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object FLOAT : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object INT : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object TIMESTAMP : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object UUID : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object VARCHAR : ColumnType<String> {
        override fun interpret(data: ByteArray) = data.toString(Charsets.UTF_8)
    }

    object VARINT : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object TIMEUUID : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    object INET : ColumnType<ByteArray> {
        override fun interpret(data: ByteArray) = data
    }

    data class LIST(val element: ColumnType<*>) :
        ColumnType<Any?> {
        override fun interpret(data: ByteArray) = data
    }

    data class MAP(val key: ColumnType<*>, val value: ColumnType<*>) :
        ColumnType<Any?> {
        override fun interpret(data: ByteArray) = data
    }

    data class SET(val element: ColumnType<*>) :
        ColumnType<Any?> {
        override fun interpret(data: ByteArray) = data
    }

    companion object {

        fun read(stream: ByteArrayInputStream): ColumnType<*> {
            val kind = stream.readS16_be()
            return when (kind) {
                0x0000 -> CUSTOM(stream.readCassandraString())
                0x0001 -> ASCII
                0x0002 -> BIGINT
                0x0003 -> BLOB
                0x0004 -> BOOLEAN
                0x0005 -> COUNTER
                0x0006 -> DECIMAL
                0x0007 -> DOUBLE
                0x0008 -> FLOAT
                0x0009 -> INT
                0x000B -> TIMESTAMP
                0x000C -> UUID
                0x000D -> VARCHAR
                0x000E -> VARINT
                0x000F -> TIMEUUID
                0x0010 -> INET
                0x0020 -> LIST(
                    read(
                        stream
                    )
                )
                0x0021 -> MAP(
                    read(
                        stream
                    ), read(stream)
                )
                0x0022 -> SET(
                    read(
                        stream
                    )
                )
                0x0030 -> TODO("UDT NOT IMPLEMENTED")
                0x0031 -> TODO("TUPLE NOT IMPLEMENTED")
                else -> TODO("Unsupported $kind")
            }
        }
    }
}