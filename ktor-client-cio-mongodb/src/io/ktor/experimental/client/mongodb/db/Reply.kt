package io.ktor.experimental.client.mongodb.db

import io.ktor.experimental.client.mongodb.*
import io.ktor.experimental.client.mongodb.bson.*
import io.ktor.experimental.client.util.*

data class Reply(
    val packet: Packet,
    val responseFlags: Int,
    val cursorID: Long,
    val startingFrom: Int,
    val documents: List<BsonDocument>
) {
    val firstDocument get() = documents.first()
    fun checkErrors() = this.apply {
        val errmsg = firstDocument["errmsg"]?.toString()
        val errcode = firstDocument["code"]?.toString()
        if (errmsg != null) throw MongoDBException(
            errmsg,
            errcode?.toIntOrNull() ?: -1
        )
        val writeErrors = firstDocument["writeErrors"]
        if (writeErrors != null) Dynamic {
            throw MongoDBWriteException(writeErrors.list.map {
                MongoDBException(
                    it["errmsg"].toString(),
                    it["code"].toIntDefault(-1)
                )
            })
        }
    }
}