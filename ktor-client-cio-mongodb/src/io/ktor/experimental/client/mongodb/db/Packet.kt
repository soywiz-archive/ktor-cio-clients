package io.ktor.experimental.client.mongodb.db

class Packet(
    val opcode: Int,
    val requestId: Int,
    val responseTo: Int,
    val payload: ByteArray
) {
    override fun toString(): String =
        "MongoPacket(opcode=$opcode, requestId=$requestId, responseTo=$responseTo, payload=SIZE(${payload.size}))"
}