package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.postgre.protocol.*
import io.ktor.experimental.client.postgre.scheme.*
import io.ktor.experimental.client.postgre.stats.*
import io.ktor.experimental.client.util.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.sync.*
import kotlinx.io.core.*
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


interface PostgreClient : DBClient {
    val notices: Signal<PostgreException>
}

fun PostgreClient(
    user: String,
    password: String? = null,
    database: String = user,
    config: PostgreConfig = PostgreConfig(),
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
    config: PostgreConfig = PostgreConfig()
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
    val config: PostgreConfig,
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

            debug { "PACKET: ${packet.typeChar} : ${packet.payload.readText()}" }

            when (packet.typeChar) {
                'T' -> { // RowDescription (B)
                    // https://www.postgresql.org/docs/9.2/static/catalog-pg-type.html

                    with(packet.payload) {
                        val count = readShort().toInt() and 0xffff

                        columns = DbColumns((0 until count).map {
                            PostgreColumn(
                                index = it,
                                name = readStringz(),
                                tableOID = readInt(),
                                columnIndex = readShort().toInt() and 0xffff,
                                typeOID = readInt(),
                                typeSize = readShort().toInt() and 0xffff,
                                typeMod = readInt(),
                                text = readShort().toInt() == 0
                            )
                        })
                    }

                }
                'D' -> { // DataRow (B)
                    with(packet.payload) {
                        val count = readShort().toInt() and 0xffff

                        rows += DbRow(columns, (0 until count).map {
                            val packetSize = readInt()
                            readBytes(packetSize)
                        })
                    }
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
            val result = parsePacket("", packet)
            when (result) {
                is EXCEPTION -> exception = result.exception
                END -> break@loop // Ready for query
                else -> Unit
            }
        }
        if (exception != null) throw exception
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
