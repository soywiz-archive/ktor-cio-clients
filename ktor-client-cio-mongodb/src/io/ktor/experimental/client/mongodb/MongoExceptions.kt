package io.ktor.experimental.client.mongodb


open class MongoDBException(message: String, val code: Int = -1) : RuntimeException(message)

open class MongoDBWriteException(val exceptions: List<MongoDBException>) :
    MongoDBException(exceptions.map { it.message }.joinToString(", "))

class MongoDBFileNotFoundException(val file: String) : MongoDBException("Can't find file '$file'", -1)
