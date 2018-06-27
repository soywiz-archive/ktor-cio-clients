package io.ktor.experimental.client.cassandra

import io.ktor.experimental.client.cassandra.db.*
import io.ktor.experimental.client.util.sync.*
import java.io.*


internal fun ByteArrayInputStream.readCassandraColumnType(): ColumnType<*> = ColumnType.read(this)
internal fun ByteArrayInputStream.readCassandraString(): String = readString(this.readS16_be(), Charsets.UTF_8)
internal fun ByteArrayInputStream.readCassandraLongString(): String = this.readString(this.readS32_be(), Charsets.UTF_8)
internal fun ByteArrayInputStream.readCassandraShortBytes(): ByteArray = this.readBytesExact(this.readS16_be())
internal fun ByteArrayInputStream.readCassandraBytes(): ByteArray = this.readBytesExact(this.readS32_be())

internal fun ByteArrayOutputStream.writeCassandraString(str: String) {
    val data = str.toByteArray(Charsets.UTF_8)
    this.write16_be(data.size)
    this.writeBytes(data)
}

internal fun ByteArrayOutputStream.writeCassandraLongString(str: String) {
    val data = str.toByteArray(Charsets.UTF_8)
    this.write32_be(data.size)
    this.writeBytes(data)
}

internal fun ByteArrayOutputStream.writeCassandraStringList(strs: List<String>) {
    this.write16_be(strs.size)
    for (str in strs) writeCassandraString(str)
}

internal fun ByteArrayOutputStream.writeCassandraStringMap(map: Map<String, String>) {
    this.write16_be(map.size)
    for ((k, v) in map) {
        this.writeCassandraString(k)
        this.writeCassandraString(v)
    }
}

internal fun ByteArrayOutputStream.writeCassandraStringMultiMap(map: Map<String, List<String>>) {
    this.write16_be(map.size)
    for ((k, v) in map) {
        this.writeCassandraString(k)
        this.writeCassandraStringList(v)
    }
}
