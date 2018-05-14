package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*

object MongoDBQueryBuilder {
    infix fun String.eq(value: Any?): BsonDocument = mapOf(this to mapOf("\$eq" to value))
    infix fun String.ne(value: Any?): BsonDocument = mapOf(this to mapOf("\$ne" to value))
    infix fun String.gt(value: Any?): BsonDocument = mapOf(this to mapOf("\$gt" to value))
    infix fun String.gte(value: Any?): BsonDocument = mapOf(this to mapOf("\$gte" to value))
    infix fun String.lt(value: Any?): BsonDocument = mapOf(this to mapOf("\$lt" to value))
    infix fun String.lte(value: Any?): BsonDocument = mapOf(this to mapOf("\$lte" to value))
    infix fun String._in(value: List<Any?>): BsonDocument = mapOf(this to mapOf("\$in" to value))
    infix fun String._nin(value: List<Any?>): BsonDocument = mapOf(this to mapOf("\$nin" to value))

    fun and(vararg items: BsonDocument): BsonDocument = mapOf("\$and" to items.toList())
    fun or(vararg items: BsonDocument): BsonDocument = mapOf("\$or" to items.toList())
    fun nor(vararg items: BsonDocument): BsonDocument = mapOf("\$nor" to items.toList())

    infix fun BsonDocument.and(other: BsonDocument): BsonDocument = and(this, other)
    infix fun BsonDocument.or(other: BsonDocument): BsonDocument = or(this, other)
    infix fun BsonDocument.nor(other: BsonDocument): BsonDocument = nor(this, other)
    fun BsonDocument.not(): BsonDocument = mapOf("\$not" to this)

    fun String.exists(exists: Boolean = true) = mapOf(this to mapOf("\$exists" to exists))
    fun String.eqType(type: String) = mapOf(this to mapOf("\$type" to type))
}

inline fun mongoQuery(generate: MongoDBQueryBuilder.() -> BsonDocument): BsonDocument = generate(MongoDBQueryBuilder)

fun MongoDBQueryBuilder.all(): BsonDocument = mapOf()

/*
fun test() {
    mongoQuery { ("test" eq 10) or ("test" eq 20) }
}
*/
