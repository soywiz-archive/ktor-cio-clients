package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.postgre.db.*
import io.ktor.experimental.client.postgre.stats.*
import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.sync.*
import org.slf4j.*
import java.lang.management.*
import java.net.*
import java.util.*
import java.util.concurrent.atomic.*
import javax.management.*


// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html
// https://www.postgresql.org/docs/9.2/static/datatype-oid.html

sealed class ParseResult
object CONTINUE : ParseResult()
object END : ParseResult()
class EXCEPTION(val exception: PostgreException) : ParseResult()

interface PostgreClient : DBClient {
    val notices: Signal<PostgreException>
}

fun PostgreClient(
    user: String,
    password: String? = null,
    database: String = user,
    config: PostgresConfig = PostgresConfig(),
    connector: suspend () -> DbClientConnection
): PostgreClient = InternalPostgreClient(
    DbClientProperties(
        user, password, database,
        debug = config.debug
    ), config, connector
)

suspend fun PostgreClient(
    user: String,
    host: String = "127.0.0.1",
    port: Int = 5432,
    password: String? = null,
    database: String = user,
    config: PostgresConfig = PostgresConfig()
): PostgreClient = PostgreClient(user, password, database, config = config) {
    val socket = aSocket(ActorSelectorManager(ioCoroutineDispatcher)).tcp().connect(InetSocketAddress(host, port))
    DbClientConnection(
        socket.openReadChannel(),
        socket.openWriteChannel(autoFlush = false),
        socket
    )
}

internal class InternalPostgreClient(
    val props: DbClientProperties,
    val config: PostgresConfig,
    val connector: suspend () -> DbClientConnection,
    private val dispatcher: CoroutineDispatcher = DefaultDispatcher
) : PostgreClient, WithProperties by WithProperties.Mixin() {
    override val context: Job = Job()
    override val notices = Signal<PostgreException>()

    private val logger = LoggerFactory.getLogger("postgre")
    var params = LinkedHashMap<String, String>()
    var processId = 0
    var processSecretKey = 0
    var errorCount = 0

    private val connectionHolder = Mutex()

    private val initializer = async { initClient() }

    var preconnectionTime: Date? = null
    var connectionTime: Date? = null
    var connection: DbClientConnection? = null
    val write get() = connection!!.write
    val read get() = connection!!.read
    val close get() = connection!!.close

    var queryStarted = 0
    var queryCompleted = 0

    val stats = PostgreStats(this)
    val mbeanId = mbeanLastId.getAndIncrement()
    val mbeanName = ObjectName("postgresql:type=Client$mbeanId")

    init {
        ManagementFactory.getPlatformMBeanServer().registerMBean(stats, mbeanName)
    }

    override suspend fun query(query: String): DbRowSet {
        initializer.await()

        queryStarted++
        return try {
            connectionHolder.lock()
            withContext(dispatcher) {
                try {
                    writeQuery(query)
                    return@withContext readQueryResult(query)
                } catch (cause: PostgreException) {
                    throw cause
                } catch (cause: Throwable) {
                    errorCount++
                    closeConnection()
                    System.err.println("[Postgres] ${cause.javaClass} : ${cause.message}")
                    throw cause
                }
            }
        } finally {
            queryCompleted++
            connectionHolder.unlock()
        }
    }

    override fun close() {
        initializer.cancel()
        close.close()
    }

    private suspend fun initClient() {
        try {
            preconnectionTime = Date()
            connection = connector()
            connectionTime = Date()
            write.writePostgreStartup(props.user, props.database)
            readUpToReadyForQuery()
        } catch (cause: Throwable) {
            val message = when (cause) {
                is ConnectException -> "[Postgres] ConnectException: "
                is ClosedReceiveChannelException -> "[Postgres] ClosedReceiveChannelException: "
                else -> "[Postgres] error:"
            }

            errorCount++
            System.err.println("$message: $cause")
            cause.printStackTrace()

            closeConnection()
        }
    }

    private suspend fun writeQuery(query: String) {
        val postgrePacket = PostgrePacket('Q') { writeStringz(query) }
        write.writePostgrePacket(postgrePacket)
    }

    private suspend fun readQueryResult(query: String): DbRowSet {
        var exception: PostgreException? = null
        var columns = DbColumns(listOf())
        val rows = arrayListOf<DbRow>()

        read@ while (true) {
            val packet = read.readPostgrePacket(config)

            debug { "PACKET: ${packet.typeChar} : ${packet.payload.toString(Charsets.UTF_8)}" }

            when (packet.typeChar) {
                'T' -> { // RowDescription (B)
                    // https://www.postgresql.org/docs/9.2/static/catalog-pg-type.html

                    val column = packet.read {
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
                    }

                    columns = DbColumns(column)
                }
                'D' -> { // DataRow (B)
                    val cells = packet.read {
                        (0 until readU16_be()).map { readBytesExact(readS32_be()) }
                    }

                    rows += DbRow(columns, cells)
                }
                else -> {
                    val result = parsePacket(query, packet)
                    when (result) {
                        is EXCEPTION -> exception = result.exception
                        END -> break@read // Ready for query
                        else -> Unit
                    }
                }
            }
        }

        if (exception != null) throw exception
        return DbRowSet(columns, rows.toSuspendingSequence(), DbRowSetInfo(query = query))
    }

    private suspend fun readUpToReadyForQuery() {
        var exception: PostgreException? = null
        loop@ while (true) {
            val packet = read.readPostgrePacket(config)
            val res = parsePacket("", packet)
            when (res) {
                is EXCEPTION -> exception = res.exception
                END -> break@loop // Ready for query
                else -> Unit
            }
        }
        if (exception != null) throw exception
    }

    private fun parsePacket(query: String, packet: PostgrePacket): ParseResult {
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
                    'E' -> return EXCEPTION(
                        PostgreException(
                            query,
                            parts
                        )
                    )
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

    fun closeConnection() {
        ignoreErrors { connection?.close?.close() }
        connection = null
    }

    private inline fun debug(msg: () -> String) {
        if (logger.isDebugEnabled || props.debug) {
            val m = msg()
            if (logger.isDebugEnabled) logger.debug(m)
            if (props.debug) println(m)
        }
    }

    companion object {
        private var mbeanLastId = AtomicInteger()
    }

    // TODO: remove unsused
    protected fun finalize() {
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName)
    }
}
