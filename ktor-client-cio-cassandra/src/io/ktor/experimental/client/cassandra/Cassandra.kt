package io.ktor.experimental.client.cassandra

import io.ktor.experimental.client.cassandra.db.*
import io.ktor.experimental.client.cassandra.db.Channel
import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import org.intellij.lang.annotations.*
import java.io.*
import java.net.*

const val DEFAULT_CASSANDRA_BUFFER_SIZE = 0x1000
const val DEFAULT_CASSANDRA_PORT = 9042

// https://raw.githubusercontent.com/apache/cassandra/trunk/doc/native_protocol_v3.spec
class Cassandra private constructor(
    private val reader: ByteReadChannel,
    private val writer: ByteWriteChannel,
    private val close: Closeable,
    private val bufferSize: Int = DEFAULT_CASSANDRA_BUFFER_SIZE,
    private val debug: Boolean = false,
    private val dispatcher: CoroutineDispatcher = DefaultDispatcher
) : Closeable {
    val context: Job = Job()

    private val channels = ArrayList<Channel>()

    private val outgoing = actor<Packet>(dispatcher, parent = context) {
        try {
            consumeEach { packet ->
                writer.writeFully(packet.toByteArray())
            }
        } finally {
            close.close()
        }
    }

    companion object {
        suspend operator fun invoke(
            host: String = "127.0.0.1",
            port: Int = DEFAULT_CASSANDRA_PORT,
            debug: Boolean = false
        ): Cassandra {
            val bufferSize = 0x1000
            val client = aSocket(ActorSelectorManager(ioCoroutineDispatcher))
                .tcp().connect(InetSocketAddress(host, port))

            return Cassandra(
                reader = client.openReadChannel(),
                writer = client.openWriteChannel(autoFlush = true),
                close = client,
                bufferSize = bufferSize,
                debug = debug
            )
        }

        /**
         * Constructor used for testing
         */
        suspend operator fun invoke(
            reader: ByteReadChannel,
            writer: ByteWriteChannel,
            close: Closeable,
            bufferSize: Int = 0x1000,
            debug: Boolean = false
        ): Cassandra {
            val result = Cassandra(reader, writer, close, bufferSize, debug)
            launch {
                try {
                    result.init()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }.start()
            result.readyDeferred.await()
            return result
        }
    }

    private val readyDeferred = CompletableDeferred<Unit>()
    val ready = readyDeferred


    private val availableChannels = AsyncPool(factory = ObjectFactory {
        Channel(channels.size).apply { channels += this }
    })

    suspend fun init() {
        availableChannels.borrow()
        sendStartup()
        while (true) {
            val packet = Packet.read(reader)
            log("RECV packet[${packet.stream}]: $packet")
            // stream 0 used here for internal usage, while negative ones are from server
            if (packet.stream <= 0) {
                try {
                    when (packet.opcode) {
                        Opcodes.READY -> readyDeferred.complete(Unit)
                        else -> TODO("Unsupported root opcode ${packet.opcode}")
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } else {
                val channel = channels.getOrNull(packet.stream)
                channel?.data?.produce(packet)
            }
        }
    }

    private suspend fun <T> allocStream(callback: suspend (Channel) -> T): T = availableChannels.use { callback(it) }

    suspend fun createKeyspace(
        namespace: String,
        ifNotExists: Boolean = true,
        dc1: Int = 1,
        dc2: Int = 3,
        durableWrites: Boolean = false
    ) {
        val ifNotExistsStr = if (ifNotExists) " IF NOT EXISTS" else ""
        // @TODO: Use parameters instead of $name
        query("CREATE KEYSPACE$ifNotExistsStr $namespace WITH replication = {'class': 'NetworkTopologyStrategy', 'DC1' : $dc1, 'DC2' : $dc2} AND durable_writes = $durableWrites;")
    }

    // @TODO: Use parameters $namespace
    suspend fun use(namespace: String): String = query("USE $namespace;")[0].getString(0)

    suspend fun useOrCreate(namespace: String): String {
        createKeyspace(namespace, ifNotExists = true)
        return use(namespace)
    }

    suspend fun query(@Language("cql") query: String, consistency: Consistency = Consistency.ONE): Rows {
        ready.await()
        return allocStream { channel ->
            outgoing.send(
                Packet(
                    opcode = Opcodes.QUERY,
                    stream = channel.id,
                    payload = MemorySyncStreamToByteArray {
                        val flags = 0
                        writeCassandraLongString(query)
                        write16_be(consistency.value)
                        write8(flags)
                    }
                )
            )
            val result = channel.data.consume()
            val res = if (result != null) interpretPacket(result) else Unit
            when (res) {
                is Rows -> res
                is String -> {
                    val columns = Columns(
                        listOf(
                            Column(
                                0,
                                "",
                                "",
                                "value",
                                ColumnType.VARCHAR
                            )
                        )
                    )
                    Rows(
                        columns,
                        listOf(
                            Row(
                                columns,
                                listOf(res.toByteArray(Charsets.UTF_8))
                            )
                        )
                    )
                }
                else -> {
                    Rows(
                        Columns(
                            listOf()
                        ), listOf()
                    )
                }
            }
        }
    }

    data class CassandraException(val errorCode: Int, val errorMessage: String) : Exception(errorMessage)

    private fun interpretPacket(packet: Packet): Any {
        val payload = packet.payload
        return when (packet.opcode) {
            Opcodes.ERROR -> {
                val stream = payload.openSync()
                val errorCode = stream.readS32_be()
                val errorMessage = stream.readCassandraString()
                throw CassandraException(errorCode, errorMessage)
            }
            Opcodes.RESULT -> {
                val stream = payload.openSync()
                val kind = stream.readS32_be()
                //println("result: $kind")
                // Set_keyspace
                // Prepared
                // Schema_change
                when (kind) {
                // Void, no info
                    0x0001 -> Unit
                    0x0002 -> { // Rows
                        val flags = stream.readS32_be()
                        val columns_count = stream.readS32_be()

                        val useGlobalTableSpec = (flags and 0b001) != 0
                        val hasMorePages = (flags and 0b010) != 0
                        val noMetadata = (flags and 0b100) != 0

                        val globalTableSpec = if (useGlobalTableSpec)
                            Pair(stream.readCassandraString(), stream.readCassandraString()) else null

                        val columnsList = ArrayList<Column>()
                        for (n in 0 until columns_count) {
                            val spec = globalTableSpec
                                    ?: Pair(stream.readCassandraString(), stream.readCassandraString())

                            val name = stream.readCassandraString()
                            val type = stream.readCassandraColumnType()

                            columnsList += Column(
                                columnsList.size,
                                spec.first, spec.second,
                                name, type
                            )
                        }

                        val columns = Columns(columnsList)
                        val rowsList = ArrayList<Row>()
                        val rows_count = stream.readS32_be()
                        for (n in 0 until rows_count) {
                            val cells = ArrayList<ByteArray>()
                            for (m in 0 until columns_count) {
                                cells += stream.readCassandraBytes()
                            }
                            rowsList += Row(columns, cells)
                        }

                        Rows(columns, rowsList)
                    }
                    0x0003 -> stream.readCassandraString()
                    0x0004 -> Unit
                    0x0005 -> Unit
                    else -> TODO("Unsupported result type $kind")
                }
            }
            else -> TODO("Unsupported package ${packet.opcode}")
        }
    }

    private suspend fun sendStartup() {
        outgoing.send(Packet(
            opcode = Opcodes.STARTUP,
            stream = 0,
            payload = MemorySyncStreamToByteArray {
                writeCassandraStringMap(
                    linkedMapOf("CQL_VERSION" to "3.0.0") //"COMPRESSION"
                )
            }
        ))
    }

    private fun log(msg: String) {
        if (debug) println(msg)
    }

    override fun close() {
        outgoing.close()
    }
}

