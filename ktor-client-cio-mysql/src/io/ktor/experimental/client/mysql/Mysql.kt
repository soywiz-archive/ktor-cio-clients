package io.ktor.experimental.client.mysql

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.ByteOrder
import java.io.*
import java.io.EOFException
import java.net.*
import java.nio.charset.*
import java.security.*
import java.text.*
import kotlin.math.*

object MysqlFieldDetail {
    val NOT_NULL = 1 // field cannot be null
    val PRIMARY_KEY = 2    // field is a primary key
    val UNIQUE_KEY = 4    // field is unique
    val MULTIPLE_KEY = 8 // field is in a multiple key
    val BLOB = 16     // is this field a Blob
    val UNSIGNED = 32    // is this field unsigned
    val ZEROFILL_FLAG = 64    // is this field a zerofill
    val BINARY_COLLATION = 128    // whether this field has a binary collation
    val ENUM = 256    // Field is an enumeration
    val AUTO_INCREMENT = 512    // field auto-increment
    val TIMESTAMP = 1024    // field is a timestamp value
    val SET = 2048    // field is a SET
    val NO_DEFAULT_VALUE_FLAG = 4096    // field doesn't have default value
    val ON_UPDATE_NOW_FLAG = 8192    // field is set to NOW on UPDATE
    val NUM_FLAG = 32768    // field is num
}

enum class MysqlFieldType(val id: Int) {
    DECIMAL(0), TINY(1), SHORT(2), LONG(3), FLOAT(4), DOUBLE(5), NULL(6), TIMESTAMP(7),
    LONGLONG(8), INT24(9), DATE(10), TIME(11), DATETIME(12), YEAR(13), NEWDATE(14),
    VARCHAR(15), BIT(16), TIMESTAMP2(17), DATETIME2(18), TIME2(19),
    NEWDECIMAL(246), ENUM(247), TINY_BLOB(249), MEDIUM_BLOB(250), LONG_BLOB(251),
    BLOB(252), VAR_STRING(253), STRING(254), GEOMETRY(255);

    companion object {
        val BY_ID = values().map { it.id to it }.toMap()

        operator fun invoke(id: Int) = BY_ID[id] ?: NULL
    }
}


class MysqlException(val errorCode: Int, val sqlState: String, message: String) : Exception(message) {
    override fun toString(): String = "MysqlException($errorCode, '$sqlState', '$message')"
}

interface Mysql : DbClient {
}

// https://dev.mysql.com/doc/refman/5.7/en/string-literals.html#character-escape-sequences
fun String.mysqlEscape(): String {
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

fun String.mysqlQuote(): String = "'${this.mysqlEscape()}'"
fun String.mysqlTableQuote(): String = "`${this.mysqlEscape()}`"

suspend fun Mysql.useDatabase(name: String) = query("USE ${name.mysqlTableQuote()};")

fun Mysql(
    host: String = "127.0.0.1",
    port: Int = 3306,
    user: String = "root",
    password: String = "",
    database: String? = null
): Mysql {
    return MysqlClientLazy(host, port, user, password, database)
}


class MysqlClientMulti : Mysql, WithProperties by WithProperties.Mixin() {
    override suspend fun query(query: String): DbRowSet = TODO()
    override suspend fun close() = TODO()
}

class MysqlClientLazy(
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val user: String = "root",
    val password: String = "",
    val database: String? = null
) : Mysql, WithProperties by WithProperties.Mixin() {
    private var client: Mysql? = null

    private suspend fun initOnce(): Mysql {
        if (client == null) {
            client = MysqlClient(host, port, user, password, database)
        }
        return client!!
    }

    override suspend fun query(query: String): DbRowSet = initOnce().query(query)
    override suspend fun close(): Unit = initOnce().close()
}

suspend fun ByteReadChannel.readBytesExact(count: Int): ByteArray {
    val out = ByteArray(count)
    readFully(out)
    return out
}

// Mysql protocol, compatible with MariaDB, Sphinx...
// Reference: https://mariadb.com/kb/en/library/clientserver-protocol/
// Reference: https://github.com/mysqljs/mysql/blob/master/lib/protocol/packets/HandshakeInitializationPacket.js
// Used wireshark on loopback + mysql -u root -h 127.0.0.1 --ssl-mode=DISABLED for debugging
class MysqlClient private constructor(
    private val read: ByteReadChannel,
    private val write: ByteWriteChannel,
    private val close: Closeable
) : Mysql, WithProperties by WithProperties.Mixin() {
    private val charset = Charsets.UTF_8

    private val context by lazy {
        DbRowSetContext(
            charset = charset,
            timeFormat = TIME_FORMAT,
            dateFormat = DATE_FORMAT,
            datetimeFormat = DATETIME_FORMAT
        )
    }

    companion object {
        suspend operator fun invoke(
            read: ByteReadChannel,
            write: ByteWriteChannel,
            close: Closeable,

            user: String = "root",
            password: String = "",
            database: String? = null
        ): MysqlClient {
            return MysqlClient(read, write, close).apply {
                init(user, password, database)
            }
        }

        suspend operator fun invoke(
            //client: AsyncClient,
            client: Socket,
            user: String = "root",
            password: String = "",
            database: String? = null
        ): MysqlClient {
            return invoke(
                client.openReadChannel().apply { readByteOrder = ByteOrder.LITTLE_ENDIAN },
                client.openWriteChannel(autoFlush = true).apply { writeByteOrder = ByteOrder.LITTLE_ENDIAN },
                client,
                user,
                password,
                database
            )
        }

        suspend operator fun invoke(
            host: String = "127.0.0.1",
            port: Int = 3306,
            user: String = "root",
            password: String = "",
            database: String? = null
        ): MysqlClient {
            //return invoke(AsyncClient().connect(host, port), user, password, database)
            return invoke(
                aSocket().tcp().connect(
                    InetSocketAddress(host, port)
                ), user, password, database
            )
        }

        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss")
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")
        private val DATETIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }

    private var packetNum = 0

    class Packet(val number: Int, val content: ByteArray) {
        override fun toString(): String = "Packet($number, ${content.hex})"
    }

    private suspend fun readPacket(): Packet {
        val header = read.readBytesExact(4).openSync()
        val size = header.readS24_le()
        val type = header.readU8()
        return Packet(type, read.readBytesExact(size))
    }

    private suspend fun writePacket(packet: Packet) {
        write.writeFully(MemorySyncStreamToByteArray {
            write24_le(packet.content.size)
            write8(packet.number)
            writeBytes(packet.content)
        })
    }

    private suspend inline fun writePacket(number: Int = packetNum++, builder: ByteArrayOutputStream.() -> Unit) {
        writePacket(
            Packet(
                number,
                MemorySyncStreamToByteArray { builder() })
        )
    }

    private suspend inline fun <T> readPacket(builder: ByteArrayInputStream.(Packet) -> T): T {
        val packet = readPacket()
        this.packetNum = packet.number + 1
        return builder(packet.content.openSync(), packet)
    }

    /**
     * https://mariadb.com/kb/en/library/com_quit/
     */
    private suspend fun writeQuit() = writePacket { write8(1) }

    /**
     * https://mariadb.com/kb/en/library/com_query/
     */
    private suspend fun writeQuery(query: String) = writePacket {
        write8(3)
        writeBytes(query.toByteArray(charset))
    }

    class ServerInfo(
        val serverCapabilities: Int,
        val serverCapabilitiesMaria: Int,
        val authPluginName: String?,
        val scramble: ByteArray
    )

    /**
     * https://mariadb.com/kb/en/library/1-connecting-connecting/
     */
    private suspend fun readHandshake(): ServerInfo = readPacket {
        val MYSQL_CLIENT = 1
        val CLIENT_SECURE_CONNECTION = 0x00008000
        val PLUGIN_AUTH = 0x00080000

        val protocolVersion = readU8()
        val serverVersion = readStringz(charset)
        val connectionID = readS32_le()
        val scramble1 = readBytesExact(8)
        val reserved1 = readU8()
        val serverCapabilities1 = readU16_le()
        val serverDefaultCollation = readU8()
        val statusFlags = readS16_le()
        val serverCapabilities2 = readU16_le()
        val scramble2Len = if ((serverCapabilities1 and MYSQL_CLIENT) != 0) {
            -1
        } else {
            readU8()
        }
        val pluginLen = readU8()
        val filler = readBytesExact(6)
        val serverCapabilitiesMaria = readS32_le()

        val serverCapabilities = serverCapabilities1 or (serverCapabilities2 shl 16)

        val scramble2 = if ((serverCapabilities and CLIENT_SECURE_CONNECTION) != 0) {
            //val scramble2Len = if (scramble2Len < 0) max(12, pluginLen - 9) else scramble2Len
            val scramble2Len = max(12, available() - pluginLen - 2)
            val scramble2 = readBytesExact(scramble2Len)
            val reserved2 = readU8()
            scramble2
        } else {
            byteArrayOf()
        }
        val authPluginName = if ((serverCapabilities and PLUGIN_AUTH) != 0) {
            readStringz(charset)
        } else {
            null
        }

        ServerInfo(
            serverCapabilities,
            serverCapabilitiesMaria,
            authPluginName,
            scramble1 + scramble2
        )
    }

    private suspend fun writeHandshake(
        serverInfo: ServerInfo, user: String, password: String, database: String?
    ) = writePacket {
        // Constants
        val CLIENT_MYSQL = 1
        val CLIENT_LONG_PASSWORD = 0b1
        val CLIENT_LONG_COLUMN_FLAGS = 0b100
        val CLIENT_CONNECT_WITH_DB = 0b1000
        val CLIENT_SPEAKS_41 = 0b1000000000
        val CLIENT_CONNECT_ATTRS = 1 shl 20

        val SERVER_CAN_DO_AUTH_41 = 0b1_000_0000_0000_0000

        val serverCapabilities = serverInfo.serverCapabilities
        var clientCapabilities = 0
        val maxPacketSize = 1 shl 23
        val clientCollation = 33
        val extendedClientCapabilities = 0
        val authPluginName = serverInfo.authPluginName
        val auth41 = (serverCapabilities and SERVER_CAN_DO_AUTH_41) != 0

        clientCapabilities = clientCapabilities or
                CLIENT_LONG_PASSWORD or
                CLIENT_SPEAKS_41 or
                CLIENT_LONG_COLUMN_FLAGS or
                CLIENT_CONNECT_ATTRS

        if (database != null) clientCapabilities = clientCapabilities or CLIENT_CONNECT_WITH_DB

        write32_le(clientCapabilities)
        write32_le(maxPacketSize)
        write8(clientCollation)
        writeBytes(ByteArray(19)) // reserved
        write32_le(extendedClientCapabilities)
        writeStringz(user)
        if (password != "") {
            if (auth41) {
                val stage1 = sha1(password.toByteArray(charset))
                val stage2 = sha1(stage1)
                val stage3 = sha1(serverInfo.scramble + stage2)
                val hashedPassword = xor(stage3, stage1)
                writeLenencBytes(hashedPassword) // password data!
            } else {
                TODO("Not handled PRE 4.1 authentication")
            }
        } else {
            writeLenencBytes(byteArrayOf()) // password data!
        }
        database?.let { writeStringz(it) }
        authPluginName?.let { writeStringz(it) }
        val attrs = listOf(
            "_os" to "unknown",
            "_client_name" to "ktor",
            "_pid" to "-1",
            "_client_version" to "0.0.1",
            "_platform" to "unknown",
            "program_name" to "ktor"
        )
        for ((key, value) in attrs) {
            writeLenencString(key, charset)
            writeLenencString(value, charset)
        }
    }

    private fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA1").digest(data)
    private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size).apply {
        for (n in 0 until this.size) this[n] = (a[n].toInt() xor b[n].toInt()).toByte()
    }


    private suspend fun readResponse(query: String): DbRowSet = readPacket {
        val kind = readU8()
        when (kind) {
            0x00 -> { // OK
                val affectedRows = readLenenc()
                val lastInsertId = readLenenc()
                val serverStatus = readU16_le()
                val warningCount = readU16_le()
                val info = readBytesAvailable()
                DbRowSet(
                    DbColumns(listOf(), context),
                    listOf<DbRow>().toSuspendingSequence(),
                    DbRowSetInfo(query = query)
                )
            }
            0xFB, 0xFE -> { // LOCAL_INFILE, PACKET_LOCAL_INFILE
                val filename = readEofString(charset)
                TODO()
            }
            0xFF -> { // ERR - https://mariadb.com/kb/en/library/err_packet/
                val errorCode = readS16_le()
                if (errorCode == -1) {
                    val stage = readU8()
                    val maxStage = readU8()
                    val progress = readU24_le()
                    val progressInfo = readLenencString(charset)
                    throw MysqlException(errorCode, "", progressInfo)
                } else {
                    var str = readEofString(charset)
                    var sqlState = ""
                    if (str.startsWith('#')) {
                        sqlState = str.substring(1, 6)
                        str = str.substring(6)
                    }
                    throw MysqlException(errorCode, sqlState, str)
                }
            }
            else -> { // ResultSet
                // @TODO: Produce rows asynchronously
                val columnCount: Int = when (kind) {
                    0xFC -> readU16_le()
                    0xFD -> readU24_le()
                    else -> kind
                }
                val columns = DbColumns((0 until columnCount).map { index ->
                    // https://mariadb.com/kb/en/library/resultset/#column-definition-packet
                    readPacket {
                        MysqlColumn(
                            index = index,
                            catalog = readLenencString(charset),
                            schema = readLenencString(charset),
                            tableAlias = readLenencString(charset),
                            table = readLenencString(charset),
                            columnAlias = readLenencString(charset),
                            column = readLenencString(charset),
                            lenFixedFields = readLenenc(),
                            characterSet = readU16_le(),
                            maxColumnSize = readS32_le(),
                            fieldType = MysqlFieldType(readU8()),
                            fieldDetailFlag = readS16_le(),
                            decimals = readU8(),
                            unused = readS16_le()
                        )
                    }
                }, context)

                readPacket {
                    // EOF
                }

                // https://mariadb.com/kb/en/library/resultset-row/
                val rows = arrayListOf<DbRow>()
                try {
                    while (true) {
                        rows += readPacket { packet ->
                            val cells = arrayListOf<ByteArray?>()
                            for (column in columns) {
                                val data = try {
                                    readLenencBytes()
                                } catch (e: NullPointerException) {
                                    null
                                }
                                cells += data
                                //println("DATA: ${column.fieldType}, $data")
                                //column.fieldDetailFlag and MysqlFieldDetail.NOT_NULL
                                /*
                                cells += if (data == null) null else when ((column as MysqlColumn).fieldType) {
                                    MysqlFieldType.NULL -> null
                                    MysqlFieldType.TINY, MysqlFieldType.SHORT, MysqlFieldType.LONG, MysqlFieldType.INT24, MysqlFieldType.YEAR ->
                                        data.toString(charset).toInt()
                                    MysqlFieldType.LONGLONG -> data.toString(charset).toLong()
                                    MysqlFieldType.FLOAT -> data.toString(charset).toFloat()
                                    MysqlFieldType.DOUBLE -> data.toString(charset).toDouble()
                                    MysqlFieldType.VAR_STRING, MysqlFieldType.STRING, MysqlFieldType.VARCHAR ->
                                        data.toString(charset)
                                    MysqlFieldType.TINY_BLOB, MysqlFieldType.MEDIUM_BLOB, MysqlFieldType.BLOB, MysqlFieldType.LONG_BLOB ->
                                        data
                                    MysqlFieldType.NEWDECIMAL, MysqlFieldType.DECIMAL ->
                                        BigDecimal(data.toString(charset))
                                    MysqlFieldType.DATE -> DATE_FORMAT.parse(data.toString(charset))
                                    MysqlFieldType.TIME -> TIME_FORMAT.parse(data.toString(charset))
                                    MysqlFieldType.DATETIME -> DATETIME_FORMAT.parse(data.toString(charset))
                                    MysqlFieldType.TIMESTAMP -> TODO()
                                    MysqlFieldType.NEWDATE -> TODO()
                                    MysqlFieldType.BIT -> TODO()
                                    MysqlFieldType.TIMESTAMP2 -> TODO()
                                    MysqlFieldType.DATETIME2 -> TODO()
                                    MysqlFieldType.TIME2 -> TODO()
                                    MysqlFieldType.ENUM -> TODO()
                                    MysqlFieldType.GEOMETRY -> TODO()
                                }
                                */
                            }
                            DbRow(columns, cells)
                        }
                    }
                } catch (e: EOFException) {

                }
                DbRowSet(
                    columns,
                    rows.toSuspendingSequence(),
                    DbRowSetInfo(query = query)
                )
            }

        }
    }

    suspend private fun init(user: String, password: String, database: String?) {
        val serverInfo = readHandshake()
        writeHandshake(serverInfo, user, password, database)
        readResponse("hanshake")
    }

    override suspend fun query(query: String): DbRowSet {
        // @TODO: Wait/force completion of the previous query
        packetNum = 0
        writeQuery(query)
        return readResponse(query)
    }

    override suspend fun close() {
        packetNum = 0
        try {
            writeQuit()
        } catch (e: Throwable) {

        }
        close.close()
    }

    fun InputStream.readLenencSmall(): Int {
        val v = readU8()
        return when (v) {
            0xFB -> throw NullPointerException()
            0xFC -> readU16_le()
            0xFD -> readU24_le()
            0xFE -> throw EOFException()
            0xFF -> throw IllegalArgumentException()
            else -> v
        }
    }

    fun InputStream.readLenenc(): Long {
        val v = readU8()
        return when (v) {
            0xFC -> readU16_le().toLong()
            0xFD -> readU24_le().toLong()
            0xFE -> readS64_le()
            0xFF -> throw IllegalArgumentException()
            else -> v.toLong()
        }
    }

    fun InputStream.readLenencBytes() = readBytesExact(readLenencSmall())

    fun InputStream.readLenencString(charset: Charset): String =
        readLenencBytes().toString(charset)

    fun InputStream.readEofString(charset: Charset): String = readBytesAvailable().toString(charset)

    fun OutputStream.writeLenencString(str: String, charset: Charset) =
        writeLenencBytes(str.toByteArray(charset))

    fun OutputStream.writeLenencBytes(data: ByteArray) {
        writeLenenc(data.size)
        writeBytes(data)
    }

    fun OutputStream.writeLenenc(value: Int) = writeLenenc(value.toLong())

    fun OutputStream.writeLenenc(value: Long) {
        when (value) {
            in 0..0xFB -> write8(value.toInt())
            in 0xFC..0xFFFF -> write16_le(value.toInt())
            in 0x10000..0xFFFFFF -> write24_le(value.toInt())
            else -> write64_le(value)
        }
    }
}

data class MysqlColumn(
    override val index: Int,
    val catalog: String,
    val schema: String,
    val tableAlias: String,
    val table: String,
    val columnAlias: String,
    val column: String,
    val lenFixedFields: Long,
    val characterSet: Int,
    val maxColumnSize: Int,
    val fieldType: MysqlFieldType,
    val fieldDetailFlag: Int, // MysqlFieldDetail
    val decimals: Int,
    val unused: Int
) : DbColumn {
    override val name get() = column
    override fun toString(): String = "MysqlColumn($columnAlias)"
}
