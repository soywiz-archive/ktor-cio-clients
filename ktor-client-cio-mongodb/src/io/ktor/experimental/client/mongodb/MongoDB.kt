package io.ktor.experimental.client.mongodb

import io.ktor.experimental.client.mongodb.bson.*
import io.ktor.experimental.client.mongodb.bson.Bson.writeBsonCString
import io.ktor.experimental.client.mongodb.bson.Bson.writeBsonDocument
import io.ktor.experimental.client.mongodb.db.*
import io.ktor.experimental.client.mongodb.util.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder
import java.io.*
import java.net.*
import java.util.concurrent.*

// https://docs.mongodb.com/manual/reference/mongodb-wire-protocol/
fun MongoDB(host: String = "127.0.0.1", port: Int = 27017): MongoDB = MongoDBClient {
    val socket = aSocket(ActorSelectorManager(ioCoroutineDispatcher)).tcp().connect(InetSocketAddress(host, port))
    Pipes(
        read = socket.openReadChannel(),
        write = socket.openWriteChannel(autoFlush = true),
        close = Closeable { socket.close() }
    )
}.apply {
    start()
}

interface MongoDB {
    suspend fun runCommand(
        db: String, payload: BsonDocument,
        numberToSkip: Int = 0, numberToReturn: Int = 1
    ): Reply
}


suspend inline fun MongoDB.runCommand(
    db: String,
    numberToSkip: Int = 0, numberToReturn: Int = 1,
    mapGen: MutableMap<String, Any?>.() -> Unit
): Reply = runCommand(
    db,
    mongoMap(mapGen), numberToSkip, numberToReturn
)

internal class Pipes(
    val read: ByteReadChannel,
    val write: ByteWriteChannel,
    val close: Closeable
)

class MongoDBClient internal constructor(
    private val dispatcher: CoroutineDispatcher = DefaultDispatcher,
    private val pipesFactory: suspend () -> Pipes
) : MongoDB {
    private val deferreds = LinkedHashMap<Int, CompletableDeferred<Reply>>()
    private var lastRequestId: Int = 0
    private var _pipes: Pipes? = null

    private suspend fun pipes(): Pipes = withContext(dispatcher) {
        if (_pipes == null) {
            _pipes = pipesFactory()
        }
        _pipes!!
    }

    var readJob: Job? = null

    fun start() {
        launch {
            readJob = readJob()
        }
    }

    override suspend fun runCommand(
        db: String, payload: BsonDocument,
        numberToSkip: Int, numberToReturn: Int
    ): Reply = writeMongoOpQuery("$db.\$cmd", numberToSkip, numberToReturn, payload)

    private suspend fun readJob(): Job = launch {
        while (true) {
            checkErrorAndReconnect {
                val response = readParsedMongoPacket()
                val deferred = deferreds[response.packet.responseTo]
                deferred?.complete(response)
            }
        }
    }

    private suspend fun readParsedMongoPacket(): Reply {
        val packet = readRawMongoPacket()
        val payload = packet.payload.asReadPacket(ByteOrder.LITTLE_ENDIAN)
        return when (packet.opcode) {
            MongoDbOperations.OP_REPLY -> {
                payload.run {
                    val responseFlags = readInt()
                    val cursorID = readLong()
                    val startingFrom = readInt()
                    val numberReturned = readInt()
                    val documents = (0 until numberReturned).map { Bson.read(this@run) }
                    Reply(packet, responseFlags, cursorID, startingFrom, documents)
                }
            }
        //MongoOps.OP_MSG -> {
        //    Bson.apply {
        //        val flags = pp.readInt() // flags
        //        println("msg.flags:$flags")
        //        while (pp.remaining > 0) {
        //            println("REM: ${pp.remaining}")
        //            val typeBody = pp.readByte()
        //            if (typeBody.toInt() != 0) error("Unexpected section type")
        //            val len = pp.readInt()
        //            val sp = pp.readPacket(len - 4, ByteOrder.LITTLE_ENDIAN)
        //            val sname = sp.readBsonCString()
        //            println("sname:'$sname'")
        //            while (sp.remaining > 0) {
        //                println("srem: ${sp.remaining}")
        //                val doc = sp.readBsonDocument()
        //                println("doc:$doc")
        //            }
        //        }
        //    }
        //    TODO()
        //}
            else -> error("Unhandled packet ${packet.opcode}")
        }
    }

    private suspend inline fun checkErrorAndReconnect(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Throwable) {
            e.printStackTrace()
            _pipes = null
            delay(5, TimeUnit.SECONDS)
        }
    }

    private suspend fun writeRawMongoPacket(packet: Packet) = withContext(dispatcher) {
        checkErrorAndReconnect {
            val write = pipes().write
            write.writeByteOrder = ByteOrder.LITTLE_ENDIAN
            val packetBytes =
                io.ktor.experimental.client.mongodb.util.buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
                    writeInt(MONGO_MESSAGE_HEAD_SIZE + packet.payload.size)
                    writeInt(packet.requestId)
                    writeInt(packet.responseTo)
                    writeInt(packet.opcode)
                    writeFully(packet.payload)
                }.readBytes()
            //println(packetBytes.hex)
            write.writeFully(packetBytes)
            write.flush()
        }
    }

    private suspend fun writeMongoOpQuery(
        fullCollectionName: String,
        numberToSkip: Int,
        numberToReturn: Int,
        query: BsonDocument,
        returnFieldsSelector: BsonDocument? = null
    ): Reply {
        val requestId = lastRequestId++
        val deferred = CompletableDeferred<Reply>()
        deferreds[requestId] = deferred
        writeRawMongoPacket(
            Packet(
                opcode = MongoDbOperations.OP_QUERY,
                requestId = requestId,
                responseTo = 0,
                payload = io.ktor.experimental.client.mongodb.util.buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
                    apply {
                        writeInt(0) // flags
                        writeBsonCString(fullCollectionName)
                        writeInt(numberToSkip)
                        writeInt(numberToReturn)
                        writeBsonDocument(query)
                        if (returnFieldsSelector != null) {
                            writeBsonDocument(returnFieldsSelector)
                        }
                    }
                }.readBytes()
            )
        )
        val result = deferred.await()
        deferreds.remove(requestId)
        return result
    }

    //suspend fun ByteWriteChannel.writeMongoOpMsg(
    //    sections: List<Pair<String, List<BsonDocument>>>
    //) {
    //    val payload = buildPacket {
    //        writeInt(0) // flags
    //        Bson.apply {
    //            for (section in sections) {
    //                writeByte(0) // TYPE: Body
    //                writePacketWithIntLength(includeLength = true) {
    //                    writeBsonCString(section.first)
    //                    for (doc in section.second) {
    //                        writeBsonDocument(doc)
    //                    }
    //                }
    //            }
    //        }
    //    }.readBytes()
    //
    //    writeRawMongoPacket(Packet(MongoOps.OP_MSG, lastRequestId++, 0, payload))
    //}

    private suspend fun readRawMongoPacket(): Packet {
        val read = pipes().read
        val head: ByteReadPacket = read.readPacket(MONGO_MESSAGE_HEAD_SIZE).withByteOrder(ByteOrder.LITTLE_ENDIAN)
        val messageLength = head.readInt()
        val requestId = head.readInt()
        val responseTo = head.readInt()
        val opcode = head.readInt()
        val payload = read.readPacket(messageLength - MONGO_MESSAGE_HEAD_SIZE)
        return Packet(opcode, requestId, responseTo, payload.readBytes())
    }

    companion object {
        private val MONGO_MESSAGE_HEAD_SIZE = 4 * 4
    }
}
