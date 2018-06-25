package io.ktor.experimental.client.mongodb.db

object QueryFlags {
    val TAILABLE_CURSOR = (1 shl 1)
    val SLAVE_OK = (1 shl 2)
    val OP_LOG_REPLY = (1 shl 3)
    val NO_CURSOR_TIMEOUT = (1 shl 4)
    val AWAIT_DATA = (1 shl 5)
    val EXHAUST = (1 shl 6)
    val PARTIAL = (1 shl 7)
}