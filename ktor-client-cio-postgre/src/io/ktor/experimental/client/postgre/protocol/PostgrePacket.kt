package io.ktor.experimental.client.postgre.protocol

import kotlinx.io.core.*

class PostgrePacket(val typeChar: Char, val payload: ByteReadPacket) {
    /** TODO: Int -> enum */
    val type: Int get() = typeChar.toInt()

    val size: Int = payload.remaining.toInt()

    override fun toString(): String = "PostgrePacket('$typeChar'($type))(len=$size, remaining=${payload.remaining})"
}

/**
 * TODO: consider to remove
 */
fun <T> PostgrePacket.read(block: Input.() -> T): T = block(payload)
