package com.soywiz.io.ktor.client.postgre

import com.soywiz.io.ktor.client.util.*
import com.soywiz.io.ktor.client.util.sync.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*

// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html

interface PostgreClient {
    suspend fun query(str: String): PostgreRowSet
}

private class InternalPostgreClient(
    val read: ByteReadChannel,
    val write: ByteWriteChannel,
    val close: Closeable
) : PostgreClient {
    var params = LinkedHashMap<String, String>()
    var processId = 0
    var processSecretKey = 0

    suspend fun startup(user: String, password: String?, database: String) {
        write.writePostgreStartup(user, database)
        readUpToReadyForQuery()
        println(params)
    }

    private val queryQueue = AsyncQueue()

    override suspend fun query(str: String): PostgreRowSet = queryQueue {
        write.writePostgrePacket(PostgrePacket('Q') {
            writeStringz(str)
        })
        var columns = PostgreColumns(listOf())
        val rows = arrayListOf<PostgreRow>()
        read@ while (true) {
            val packet = read.readPostgrePacket()
            when (packet.typeChar) {
                'T' -> { // RowDescription (B)
                    // https://www.postgresql.org/docs/9.2/static/catalog-pg-type.html
                    columns = PostgreColumns(packet.read {
                        (0 until readU16_be()).map {
                            PostgreColumn(
                                name = readStringz(),
                                tableOID = readS32_be(),
                                columnIndex = readU16_be(),
                                typeOID = readS32_be(),
                                typeSize = readS16_be(),
                                typeMod = readS32_be(),
                                format = readU16_be()
                            )
                        }
                    })
                }
                'D' -> { // DataRow (B)
                    rows += PostgreRow(columns,packet.read {
                        (0 until readU16_be()).map {
                            readBytesExact(readS32_be())
                        }
                    })
                }
                else -> {
                    if (parsePacket(packet)) {
                        break@read // Ready for query
                    }
                }
            }
        }
        return@queryQueue PostgreRowSet(columns, rows.toSuspendingSequence())
    }

    suspend fun readUpToReadyForQuery() {
        while (true) {
            val packet = read.readPostgrePacket()
            if (parsePacket(packet)) {
                break // Ready for query
            }
        }
    }

    suspend fun parsePacket(packet: PostgrePacket): Boolean {
        when (packet.typeChar) {
            'R' -> {
                return packet.read {
                    val type = readS32_be()
                    when (type) {
                        0 -> { // AuthenticationOk
                            return@read false
                        }
                        2 -> { // AuthenticationKerberosV5
                            TODO("AuthenticationKerberosV5")
                        }
                        3 -> { // AuthenticationCleartextPassword
                            TODO("AuthenticationCleartextPassword")
                        }
                        5 -> { // AuthenticationMD5Password
                            val salt = readS32_be()
                            TODO("AuthenticationMD5Password")
                        }
                        6 -> { // AuthenticationSCMCredential
                            TODO("AuthenticationSCMCredential")
                        }
                        7 -> { // AuthenticationGSS
                            TODO("AuthenticationGSS")
                        }
                        8 -> { // AuthenticationGSSContinue
                            TODO("AuthenticationGSSContinue")
                        }
                        9 -> { // AuthenticationSSPI
                            TODO("AuthenticationSSPI")
                        }
                        else -> {
                            TODO("Authentication$type")
                        }
                    }
                }
            }
            'S' -> {
                packet.read {
                    val key = readStringz()
                    val value = readStringz()
                    params[key] = value
                }
            }
            'K' -> {
                packet.read {
                    processId = readS32_be()
                    processSecretKey = readS32_be()
                    //println("processId=$processId")
                    //println("processSecretKey=$processSecretKey")
                }

            }
            'Z' -> {
                packet.read {
                    val status = readU8().toChar()
                    // 'I' if idle (not in a transaction block)
                    // 'T' if in a transaction block
                    // 'E' if in a failed transaction
                }
                return true
            }
            'C' -> { // CommandComplete
                val command = packet.read {
                    readStringz()
                }
            }
            else -> {
                println(packet)
                println(packet.payload.toList())
                println(packet.payload.toString(Charsets.UTF_8))
            }
        }
        return false
    }
}

data class PostgreColumns(val columns: List<PostgreColumn>)

data class PostgreColumn(
    val name: String, val tableOID: Int, val columnIndex: Int, val typeOID: Int,
    val typeSize: Int,
    val typeMod: Int,
    val format: Int
)

class PostgreRow(val columns: PostgreColumns, val cellsBytes: List<ByteArray>) {
    val pairs get() = columns.columns.map { it.name }.zip(cellsBytes)
    override fun toString(): String = "PostgreRow($pairs)"
}

class PostgreRowSet(val columns: PostgreColumns, val rows: SuspendingSequence<PostgreRow>) : SuspendingSequence<PostgreRow> by rows {

}

suspend fun PostgreClient(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    close: Closeable,
    user: String,
    password: String? = null,
    database: String = user
): PostgreClient {
    val client = InternalPostgreClient(read, write, close)
    client.startup(user, password, database)
    return client
}

suspend fun PostgreClient(
    user: String,
    host: String = "127.0.0.1",
    port: Int = 5432,
    password: String? = null,
    database: String = user
): PostgreClient {
    val socket = aSocket().tcp().connect(InetSocketAddress(host, port))
    return PostgreClient(
        socket.openReadChannel(),
        socket.openWriteChannel(autoFlush = false),
        socket,
        user,
        password,
        database
    )
}

class PostgrePacket(val typeChar: Char, val payload: ByteArray) {
    val type get() = typeChar.toInt()
    override fun toString(): String = "PostgrePacket('$typeChar'($type))(len=${payload.size})"
    inline fun <T> read(callback: ByteArrayInputStream .() -> T): T = payload.read(callback)
}

inline fun PostgrePacket(typeChar: Char, callback: ByteArrayOutputStream.() -> Unit): PostgrePacket {
    return PostgrePacket(typeChar, buildByteArray(callback))
}

internal suspend fun ByteReadChannel.readPostgrePacket(): PostgrePacket {
    return _readPostgrePacket(readType = true)
}

private val POSTGRE_ENDIAN = ByteOrder.BIG_ENDIAN
//private val POSTGRE_ENDIAN = ByteOrder.LITTLE_ENDIAN

internal suspend fun ByteReadChannel._readPostgrePacket(readType: Boolean): PostgrePacket {
    readByteOrder = POSTGRE_ENDIAN
    val type = if (readType) readByte().toChar() else '\u0000'
    val size = readInt()
    return PostgrePacket(type, readBytesExact(size - 4))
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

private suspend fun ByteWriteChannel._writePostgrePacket(packet: PostgrePacket, first: Boolean) {
    writeByteOrder = POSTGRE_ENDIAN
    if (!first) writeByte(packet.type)
    writeInt(4 + packet.payload.size)
    writeFully(packet.payload)
    flush()
}

internal suspend fun ByteWriteChannel.writePostgrePacket(packet: PostgrePacket) =
    _writePostgrePacket(packet, first = false)
