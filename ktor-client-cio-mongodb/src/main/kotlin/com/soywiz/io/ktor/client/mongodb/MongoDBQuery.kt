package com.soywiz.io.ktor.client.mongodb

object MongoDBQueryBuilder {
    infix fun String.eq(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$eq" to value))
    infix fun String.ne(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$ne" to value))
    infix fun String.gt(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$gt" to value))
    infix fun String.gte(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$gte" to value))
    infix fun String.lt(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$lt" to value))
    infix fun String.lte(value: Any?): Map<String, Any?> = mapOf(this to mapOf("\$lte" to value))
    infix fun String._in(value: List<Any?>): Map<String, Any?> = mapOf(this to mapOf("\$in" to value))
    infix fun String._nin(value: List<Any?>): Map<String, Any?> = mapOf(this to mapOf("\$nin" to value))

    fun and(vararg items: Map<String, Any?>): Map<String, Any?> = mapOf("\$and" to items.toList())
    fun or(vararg items: Map<String, Any?>): Map<String, Any?> = mapOf("\$or" to items.toList())
    fun nor(vararg items: Map<String, Any?>): Map<String, Any?> = mapOf("\$nor" to items.toList())

    infix fun Map<String, Any?>.and(other: Map<String, Any?>): Map<String, Any?> = and(this, other)
    infix fun Map<String, Any?>.or(other: Map<String, Any?>): Map<String, Any?> = or(this, other)
    infix fun Map<String, Any?>.nor(other: Map<String, Any?>): Map<String, Any?> = nor(this, other)
    fun Map<String, Any?>.not(): Map<String, Any?> = mapOf("\$not" to this)

    fun String.exists(exists: Boolean = true) = mapOf(this to mapOf("\$exists" to exists))
    fun String.eqType(type: String) = mapOf(this to mapOf("\$type" to type))
}

inline fun mongoQuery(generate: MongoDBQueryBuilder.() -> Map<String, Any?>): Map<String, Any?> = generate(MongoDBQueryBuilder)

/*
fun test() {
    mongoQuery { ("test" eq 10) or ("test" eq 20) }
}
*/
