package com.soywiz.io.ktor.client.postgre

import com.soywiz.io.ktor.client.db.*
import com.soywiz.io.ktor.client.util.*
import com.soywiz.io.ktor.client.util.sync.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.time.*
import java.io.*
import java.net.*
import java.time.*

// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html
// https://www.postgresql.org/docs/9.2/static/datatype-oid.html

interface PostgreClient : DbClient {
    val notices: Signal<PostgreException>
}

private class InternalPostgreClient(
    val user: String, val password: String? = null, val database: String = user,
    val connector: suspend () -> DbClientConnection
) : PostgreClient, WithProperties by WithProperties.Mixin() {
    override val notices = Signal<PostgreException>()

    var params = LinkedHashMap<String, String>()
    var processId = 0
    var processSecretKey = 0

    private val context = DbRowSetContext()
    private val queryQueue = AsyncQueue()
    var connection: DbClientConnection? = null
    val write get() = connection!!.write
    val read get() = connection!!.read
    val close get() = connection!!.close

    suspend fun ensure(): InternalPostgreClient = queryQueue {
        while (true) {
            if (connection == null) {
                try {
                    connection = connector()
                    write.writePostgreStartup(user, database)
                    readUpToReadyForQuery()
                } catch (e: ConnectException) {
                    e.printStackTrace()
                }
            }
            if (connection != null && (read.isClosedForRead || write.isClosedForWrite)) connection = null

            // DONE
            if (connection != null) {
                break
            } else {
                // RETRY
                println("RETRY")
                delay(Duration.ofSeconds(5L))
            }
        }

        this
    }

    override suspend fun query(query: String): DbRowSet {
        ensure()
        return queryQueue {
            //println("---------------------")
            //println("QUERY: $query")
            write.writePostgrePacket(PostgrePacket('Q') {
                writeStringz(query)
            })
            var columns = DbColumns(listOf())
            val rows = arrayListOf<DbRow>()
            var exc: PostgreException? = null
            read@ while (true) {

                val packet = read.readPostgrePacket()
                //println("PACKET: ${packet.typeChar} : ${packet.payload.toString(Charsets.UTF_8)}")
                when (packet.typeChar) {
                    'T' -> { // RowDescription (B)
                        // https://www.postgresql.org/docs/9.2/static/catalog-pg-type.html
                        columns = DbColumns(packet.read {
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
                        rows += DbRow(columns, packet.read {
                            (0 until readU16_be()).map {
                                readBytesExact(readS32_be())
                            }
                        })
                    }
                    else -> {
                        val res = parsePacket(query, packet)
                        when (res) {
                            is EXC -> exc = res.exception
                            END -> break@read // Ready for query
                            else -> Unit
                        }
                    }
                }
            }
            //println("+++++++++++++++++++")
            if (exc != null) throw exc
            return@queryQueue DbRowSet(columns, rows.toSuspendingSequence(), DbRowSetInfo(query = query))
        }
    }

    suspend fun readUpToReadyForQuery() {
        var exc: PostgreException? = null
        loop@ while (true) {
            val packet = read.readPostgrePacket()
            val res = parsePacket("", packet)
            when (res) {
                is EXC -> exc = res.exception
                END -> break@loop // Ready for query
                else -> Unit
            }
        }
        if (exc != null) throw exc
    }

    interface Result
    object CONTINUE : Result
    object END : Result
    class EXC(val exception: PostgreException) : Result

    suspend fun parsePacket(query: String, packet: PostgrePacket): Result {
        val packetType = packet.typeChar
        when (packetType) {
            'R' -> {
                return packet.read {
                    val type = readS32_be()
                    when (type) {
                        0 -> { // AuthenticationOk
                            return@read CONTINUE
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
                return END
            }
            'C' -> { // CommandComplete
                val command = packet.read {
                    readStringz()
                }
                //println("CommandComplete: $command")
            }
            'E', 'N' -> {
                val parts = packet.read {
                    mapWhile({ available() > 0 }) { readStringz() }
                }
                when (packetType) {
                    'E' -> return EXC(PostgreException(query, parts))
                    'N' -> notices(PostgreException(query, parts))
                }
            }
            else -> {
                println(packet)
                println(packet.payload.toList())
                println(packet.payload.toString(Charsets.UTF_8))
            }
        }
        return CONTINUE
    }

    override suspend fun close(): Unit {
        this.close.close()
    }
}

class PostgreException(val query: String, val items: List<String>) : RuntimeException() {
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

    override val message: String? = "$severity: $pmessage ($parts) for query=$query"
}

suspend fun PostgreClient(
    user: String,
    password: String? = null,
    database: String = user,
    connector: suspend () -> DbClientConnection
): PostgreClient {
    return InternalPostgreClient(user, password, database, connector).ensure()
}

suspend fun PostgreClient(
    user: String,
    host: String = "127.0.0.1",
    port: Int = 5432,
    password: String? = null,
    database: String = user
): PostgreClient {
    return PostgreClient(user, password, database) {
        val socket = aSocket().tcp().connect(InetSocketAddress(host, port))
        DbClientConnection(
            socket.openReadChannel(),
            socket.openWriteChannel(autoFlush = false),
            socket
        )
    }
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

data class PostgreColumn(
    override val index: Int,
    override val name: String, val tableOID: Int, val columnIndex: Int, val typeOID: Int,
    val typeSize: Int,
    val typeMod: Int,
    val text: Boolean
) : DbColumn

fun String.postgreEscape(): String {
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

fun String.postgreQuote(): String = "'${this.postgreEscape()}'"
fun String.postgreTableQuote(): String = "`${this.postgreEscape()}`"