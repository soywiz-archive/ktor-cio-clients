package com.soywiz.io.ktor.client.util

import kotlinx.coroutines.experimental.io.*
import java.io.*

suspend fun ByteReadChannel.readBytesExact(count: Int, to: ByteArray = ByteArray(count)): ByteArray {
    return to.apply { readFully(to, 0, count) }
}

suspend fun ByteReadChannel.readUntil(delimitier: Byte, bufferSize: Int = 1024): ByteArray {
    val out = ByteArrayOutputStream()
    while (true) {
        val b = readByte()
        if (b == delimitier) {
            break
        }
        out.write(b.toInt())
    }
    return out.toByteArray()
}

/*
suspend fun ByteReadChannel.readUntil(delimitier: Byte, bufferSize: Int = 1024): ByteArray {
    val out = ByteArrayOutputStream()
    val delimiter = ByteBuffer.wrap(byteArrayOf(delimitier))
    val bytes = ByteArray(bufferSize)
    val buffer = ByteBuffer.wrap(bytes)
    do {
        val read = readUntilDelimiter(delimiter, buffer)
        if (read == 0) { // How do I differentiate empty/full buffer from reached the delimiter?
            this.readByte()
            break
        }
        buffer.flip()
        out.write(bytes, 0, buffer.limit())
        buffer.clear()
    } while (read >= bufferSize)
    return out.toByteArray()
}

suspend fun checkWithBufferSize(bufferSize: Int) {
    val bc = ByteChannel(true)
    bc.writeFully("HELLO\nWORLD\nAB\nA\n\n".toByteArray(Charsets.UTF_8))
    println("-------")
    for (n in 0 until 5) {
        val ba = bc.readUntil('\n'.toByte(), bufferSize = bufferSize)
        println("'" + ba.toString(Charsets.UTF_8) + "'")
    }
}

fun main(args: Array<String>): Unit {
    runBlocking {
        checkWithBufferSize(1)
        checkWithBufferSize(2)
        checkWithBufferSize(3)
        checkWithBufferSize(4)
        checkWithBufferSize(5)
        checkWithBufferSize(6)
        checkWithBufferSize(1024)
    }
}
*/
