package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.postgre.protocol.*
import io.ktor.experimental.client.postgre.scheme.*
import io.ktor.experimental.client.util.*
import io.ktor.experimental.client.util.sync.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.net.*

private val POSTGRE_SELECTOR_MANAGER = ActorSelectorManager(DefaultDispatcher)

class PostgreConnection(
    val host: String, val port: Int,
    val user: String, val password: String?,
    val database: String
) : ConnectionPipeline<String, DbRowSet>() {

    private lateinit var socket: Socket
    private lateinit var input: ByteReadChannel
    private lateinit var output: ByteWriteChannel

    override suspend fun onStart() {
        socket = aSocket(POSTGRE_SELECTOR_MANAGER)
            .tcp()
            .tcpNoDelay().connect(InetSocketAddress(host, port))

        input = socket.openReadChannel()
        output = socket.openWriteChannel()

        output.writePostgreStartup(user, database)
        waitReadyForQuery()
    }

    override suspend fun send(request: String) {
        output.writePostgrePacket(MessageType.QUERY) {
            writeStringz(request)
        }
    }

    override suspend fun receive(): DbRowSet {
        var exception: PostgreException? = null
        var columns = DbColumns(listOf())
        val rows = arrayListOf<DbRow>()

        read@ while (true) {
            val packet = input.readPostgrePacket()

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
                    val result = parsePacket(packet)
                    when (result) {
                        is EXCEPTION -> exception = result.exception
                        END -> break@read // Ready for query
                        else -> Unit
                    }
                }
            }
        }

        if (exception != null) throw exception
        return DbRowSet(columns, rows.toSuspendingSequence(), DbRowSetInfo())
    }

    override fun onDone() {
        socket.close()
    }

    private suspend fun waitReadyForQuery() {
        loop@ while (true) {
            val packet = input.readPostgrePacket()
            val result = parsePacket(packet)
            when (result) {
                is EXCEPTION -> throw result.exception
                END -> break@loop // Ready for query
                else -> Unit
            }
        }
    }
}
