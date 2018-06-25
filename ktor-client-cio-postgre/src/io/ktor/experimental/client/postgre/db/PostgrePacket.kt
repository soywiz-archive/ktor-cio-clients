package io.ktor.experimental.client.postgre.db

import io.ktor.experimental.client.util.sync.*
import java.io.*

class PostgrePacket(val typeChar: Char, val payload: ByteArray) {
    val type get() = typeChar.toInt()
    override fun toString(): String = "PostgrePacket('$typeChar'($type))(len=${payload.size})"
    inline fun <T> read(callback: ByteArrayInputStream .() -> T): T = payload.read(callback)
}