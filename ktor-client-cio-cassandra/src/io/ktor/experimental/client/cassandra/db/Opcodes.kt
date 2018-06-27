package io.ktor.experimental.client.cassandra.db

object Opcodes {
    const val ERROR = 0x00
    const val STARTUP = 0x01
    const val READY = 0x02
    const val AUTHENTICATE = 0x03
    const val OPTIONS = 0x05
    const val SUPPORTED = 0x06
    const val QUERY = 0x07
    const val RESULT = 0x08
    const val PREPARE = 0x09
    const val EXECUTE = 0x0A
    const val REGISTER = 0x0B
    const val EVENT = 0x0C
    const val BATCH = 0x0D
    const val AUTH_CHALLENGE = 0x0E
    const val AUTH_RESPONSE = 0x0F
    const val AUTH_SUCCESS = 0x10
}