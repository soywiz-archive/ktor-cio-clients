package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*

suspend fun ByteWriteChannel.writePacket(block: BytePacketBuilder.() -> Unit) {
    writePacket(buildPacket(block = block))
}