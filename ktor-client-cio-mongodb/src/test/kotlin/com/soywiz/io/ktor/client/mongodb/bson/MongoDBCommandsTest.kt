package com.soywiz.io.ktor.client.mongodb.bson

import com.fasterxml.jackson.module.kotlin.*
import com.soywiz.io.ktor.client.mongodb.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import kotlin.test.*

class MongoDBCommandsTest {
    val log = ArrayList<String>()

    fun BsonDocument.toJson() = jacksonObjectMapper().writeValueAsString(this)

    val mongo = object : MongoDB {
        override suspend fun runCommand(
            db: String,
            payload: BsonDocument,
            numberToSkip: Int,
            numberToReturn: Int
        ): MongoDB.Reply {
            log += "$db:" + payload.toJson()
            return MongoDB.Reply(
                MongoDB.Packet(0, 0, 0, byteArrayOf()), 0, 0L, 0,
                listOf(mapOf("cursor" to mapOf("firstBatch" to listOf<BsonDocument>())))
            )
        }
    }

    @Test
    fun testQuery() {
        runBlocking {
            mongo["mydb"]["mycollection"].query().skip(20).limit(10).filter { "test" eq 10 }.firstOrNull()
            assertEquals(
                listOf("""mydb:{"find":"mycollection","filter":{"test":{"${'$'}eq":10}},"skip":20,"limit":1}"""),
                log
            )
        }
    }

    @Test
    fun testDelete() {
        runBlocking {
            mongo["mydb"]["mycollection"].query().skip(20).limit(10).filter { "test" eq 10 }.deleteAll()
            mongo["mydb"]["mycollection"].query().skip(20).limit(10).filter { "test" eq 20 }.deleteOne()
            assertEquals(
                listOf(
                    """mydb:{"delete":"mycollection","deletes":[{"q":{"test":{"${'$'}eq":10}},"limit":0}]}""",
                    """mydb:{"delete":"mycollection","deletes":[{"q":{"test":{"${'$'}eq":20}},"limit":1}]}"""
                ),
                log
            )
        }
    }
}