package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*
import com.soywiz.io.ktor.client.mongodb.util.*
import com.soywiz.io.ktor.client.util.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder
import java.net.*

// https://docs.mongodb.com/manual/reference/mongodb-wire-protocol/
suspend fun MongoDB(host: String = "127.0.0.1", port: Int = 27017): MongoDB {
    val socket = aSocket().tcp().connect(InetSocketAddress("127.0.0.1", 27017))
    val read = socket.openReadChannel()
    val write = socket.openWriteChannel(autoFlush = true)
    return MongoDB(read, write).apply {
        readJob = readJob()
    }
}

class MongoDB(val read: ByteReadChannel, val write: ByteWriteChannel) {
    var lastRequestId: Int = 0

    class Packet(
        val opcode: Int,
        val requestId: Int,
        val responseTo: Int,
        val payload: ByteArray
    ) {
        override fun toString(): String =
            "MongoPacket(opcode=$opcode, requestId=$requestId, responseTo=$responseTo, payload=SIZE(${payload.size}))"
    }

    data class Reply(
        val packet: Packet,
        val responseFlags: Int,
        val cursorID: Long,
        val startingFrom: Int,
        val documents: List<Map<String, Any?>>
    ) {
        val firstDocument get() = documents.first()
        fun checkErrors() = this.apply {
            val errmsg = firstDocument["errmsg"]?.toString()
            if (errmsg != null) throw MongoDBException(errmsg)
        }
    }

    private val MONGO_MSG_HEAD_SIZE = 4 * 4

    private suspend fun _readRawMongoPacket(): Packet {
        val head: ByteReadPacket = read.readPacket(MONGO_MSG_HEAD_SIZE).withByteOrder(ByteOrder.LITTLE_ENDIAN)
        val messageLength = head.readInt()
        val requestId = head.readInt()
        val responseTo = head.readInt()
        val opcode = head.readInt()
        val payload = read.readPacket(messageLength - MONGO_MSG_HEAD_SIZE)
        return Packet(opcode, requestId, responseTo, payload.readBytes())
    }

    var readJob: Job? = null
    suspend fun readJob(): Job {
        return launch {
            while (true) {
                val response = _readParsedMongoPacket()
                val deferred = deferreds[response.packet.responseTo]
                deferred?.complete(response)
            }
        }
    }

    private suspend fun _readParsedMongoPacket(): Reply {
        val packet = _readRawMongoPacket()
        val pp = packet.payload.asReadPacket(ByteOrder.LITTLE_ENDIAN)
        when (packet.opcode) {
            MongoOps.OP_REPLY -> {
                return pp.run {
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

    private val queue = AsyncQueue()

    suspend fun writeRawMongoPacket(packet: Packet) {
        queue {
            write.writeByteOrder = ByteOrder.LITTLE_ENDIAN
            val packetBytes = buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
                writeInt(MONGO_MSG_HEAD_SIZE + packet.payload.size)
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

    private val deferreds = LinkedHashMap<Int, CompletableDeferred<Reply>>()

    suspend fun writeMongoOpQuery(
        fullCollectionName: String,
        numberToSkip: Int,
        numberToReturn: Int,
        query: Map<String, Any?>,
        returnFieldsSelector: Map<String, Any?>? = null
    ): Reply {
        val requestId = lastRequestId++
        val deferred = CompletableDeferred<Reply>()
        deferreds[requestId] = deferred
        writeRawMongoPacket(
            Packet(
                opcode = MongoOps.OP_QUERY,
                requestId = requestId,
                responseTo = 0,
                payload = buildPacket(byteOrder = ByteOrder.LITTLE_ENDIAN) {
                    Bson.apply {
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
    //    sections: List<Pair<String, List<Map<String, Any?>>>>
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

    object MongoOps {
        val OP_REPLY = 1
        val OP_UPDATE = 2001
        val OP_INSERT = 2002
        val RESERVED = 2003
        val OP_QUERY = 2004
        val OP_GET_MORE = 2005
        val OP_DELETE = 2006
        val OP_KILL_CURSORS = 2007
        val OP_COMMAND = 2010
        val OP_COMMANDREPLY = 2011
        val OP_MSG = 2013
    }

    object QueryFlags {
        val TAILABLE_CURSOR = (1 shl 1)
        val SLAVE_OK = (1 shl 2)
        val OP_LOG_REPLY = (1 shl 3)
        val NO_CURSOR_TIMEOUT = (1 shl 4)
        val AWAIT_DATA = (1 shl 5)
        val EXHAUST = (1 shl 6)
        val PARTIAL = (1 shl 7)
    }

    suspend fun runCommand(
        db: String, payload: Map<String, Any?>,
        numberToSkip: Int = 0, numberToReturn: Int = 1
    ): Reply = writeMongoOpQuery("$db.\$cmd", numberToSkip, numberToReturn, payload)

    suspend inline fun runCommand(
        db: String,
        numberToSkip: Int = 0, numberToReturn: Int = 1,
        mapGen: MutableMap<String, Any?>.() -> Unit
    ): Reply = runCommand(db, mongoMap(mapGen), numberToSkip, numberToReturn)
}

class MongoDBException(message: String) : RuntimeException(message)

fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V>): Map<K, V> {
    val out = LinkedHashMap<K, V>()
    for ((k, v) in pairs) if (v != null) out[k] = v
    return out
}

fun <K, V> MutableMap<K, V>.putNotNull(key: K, value: V) {
    if (value != null) put(key, value)
}

inline fun mongoMap(callback: MutableMap<String, Any?>.() -> Unit): Map<String, Any?> =
    LinkedHashMap<String, Any?>().apply(callback)

