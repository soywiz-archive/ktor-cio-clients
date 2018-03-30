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
// https://www.postgresql.org/docs/9.2/static/datatype-oid.html

interface PostgreClient {
    val notices: Signal<PostgreException>
    suspend fun query(str: String): PostgreRowSet
}

private class InternalPostgreClient(
    val read: ByteReadChannel,
    val write: ByteWriteChannel,
    val close: Closeable
) : PostgreClient {
    override val notices = Signal<PostgreException>()

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
                                index = it,
                                name = readStringz(),
                                tableOID = readS32_be(),
                                columnIndex = readU16_be(),
                                typeOID = readS32_be(),
                                typeSize = readS16_be(),
                                typeMod = readS32_be(),
                                text = readU16_be() == 0
                            )
                        }
                    })
                }
                'D' -> { // DataRow (B)
                    rows += PostgreRow(columns, packet.read {
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
        val packetType = packet.typeChar
        when (packetType) {
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
            'E', 'N' -> {
                val parts = packet.read {
                    mapWhile({ available() > 0 }) { readStringz() }
                }
                when (packetType) {
                    'E' -> throw PostgreException(parts)
                    'N' -> notices(PostgreException(parts))
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

class PostgreException(val items: List<String>) : RuntimeException() {
    val parts by lazy {
        items.filter { it.isNotEmpty() }.associate { it.first() to it.substring(1) }
    }

    val severity: String? get() = parts['S'] // Severity: the field contents are ERROR, FATAL, or PANIC (in an error message), or WARNING, NOTICE, DEBUG, INFO, or LOG (in a notice message), or a localized translation of one of these. Always present.
    val sqlstate: String? get() = parts['C'] // Code: the SQLSTATE code for the error (see Appendix A). Not localizable. Always present.
    val pmessage: String? get() = parts['M'] // Message: the primary human-readable error message. This should be accurate but terse (typically one line). Always present.
    val detail: String? get() = parts['D'] // Detail: an optional secondary error message carrying more detail about the problem. Might run to multiple lines.
    val hint: String? get() = parts['H'] // Hint: an optional suggestion what to do about the problem. This is intended to differ from Detail in that it offers advice (potentially inappropriate) rather than hard facts. Might run to multiple lines.
    val position: String? get() = parts['P'] // Position: the field value is a decimal ASCII integer, indicating an error cursor position as an index into the original query string. The first character has index 1, and positions are measured in characters not bytes.
    val internalPosition: String? get() = parts['p'] // Internal position: this is defined the same as the P field, but it is used when the cursor position refers to an internally generated command rather than the one submitted by the client. The q field will always appear when this field appears.
    val internalQuery: String? get() = parts['q'] // Internal query: the text of a failed internally-generated command. This could be, for example, a SQL query issued by a PL/pgSQL function.
    val where: String? get() = parts['W'] // Where: an indication of the context in which the error occurred. Presently this includes a call stack traceback of active procedural language functions and internally-generated queries. The trace is one entry per line, most recent first.
    val schemaName: String? get() = parts['s'] // Schema name: if the error was associated with a specific database object, the name of the schema containing that object, if any.
    val tableName: String? get() = parts['t'] // Table name: if the error was associated with a specific table, the name of the table. (Refer to the schema name field for the name of the table's schema.)
    val columnName: String? get() = parts['c'] // Column name: if the error was associated with a specific table column, the name of the column. (Refer to the schema and table name fields to identify the table.)
    val dataTypeName: String? get() = parts['d'] // Data type name: if the error was associated with a specific data type, the name of the data type. (Refer to the schema name field for the name of the data type's schema.)
    val contraintName: String? get() = parts['n'] // Constraint name: if the error was associated with a specific constraint, the name of the constraint. Refer to fields listed above for the associated table or domain. (For this purpose, indexes are treated as constraints, even if they weren't created with constraint syntax.)
    val fileName: String? get() = parts['F'] // File: the file name of the source-code location where the error was reported.
    val line: String? get() = parts['L'] // Line: the line number of the source-code location where the error was reported.
    val routine: String? get() = parts['R'] // Routine: the name of the source-code routine reporting the error.

    override val message: String? = "$severity: $pmessage ($parts)"
}

data class PostgreColumns(val columns: List<PostgreColumn>) : Iterable<PostgreColumn> by columns {
    val columnsByName = columns.associateBy { it.name }
    operator fun get(name: String) = columnsByName[name]
    operator fun get(index: Int) = columns.getOrNull(index)
}

data class PostgreColumn(
    val index: Int,
    val name: String, val tableOID: Int, val columnIndex: Int, val typeOID: Int,
    val typeSize: Int,
    val typeMod: Int,
    val text: Boolean
)

class PostgreRow(val columns: PostgreColumns, val cells: List<ByteArray>) : List<ByteArray> by cells {
    fun bytes(key: Int) = cells.getOrNull(key)
    fun bytes(key: String) = columns[key]?.let { bytes(it.index) }

    fun string(key: Int) = bytes(key)?.toString(Charsets.UTF_8)
    fun string(key: String) = bytes(key)?.toString(Charsets.UTF_8)

    fun int(key: Int) = string(key)?.toInt()
    fun int(key: String) = string(key)?.toInt()

    operator fun get(key: String) = bytes(key)

    val pairs get() = columns.columns.map { it.name }.zip(cells)
    override fun toString(): String = "PostgreRow($pairs)"
}

class PostgreRowSet(
    val columns: PostgreColumns,
    val rows: SuspendingSequence<PostgreRow>
) : SuspendingSequence<PostgreRow> by rows

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

private inline fun <T> mapWhile(cond: () -> Boolean, generator: () -> T): List<T> {
    val out = arrayListOf<T>()
    while (cond()) out += generator()
    return out
}