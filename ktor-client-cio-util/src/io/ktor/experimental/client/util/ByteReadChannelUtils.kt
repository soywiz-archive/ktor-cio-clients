package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteBuffer
import kotlinx.io.core.*
import java.io.*
import java.nio.*
import java.nio.charset.*

suspend fun ByteReadChannel.readBytesExact(count: Int): ByteArray {
    val packet = readPacket(count)
    if (packet.remaining != count.toLong()) error("Couldn't read exact bytes $count")
    return packet.readBytes(count)
}

// Simple version
suspend fun ByteReadChannel.readUntilString(
    delimiter: Byte,
    charset: Charset,
    bufferSize: Int = 1024,
    expectedMinSize: Int = 16
): String = readUntilString(
    out = StringBuilder(expectedMinSize),
    delimiter = ByteBuffer.wrap(byteArrayOf(delimiter)),
    decoder = charset.newDecoder(),
    charBuffer = CharBuffer.allocate(bufferSize),
    buffer = ByteBuffer.allocate(bufferSize)
).toString()

// Allocation free version
suspend fun ByteReadChannel.readUntilString(
    out: StringBuilder,
    delimiter: ByteBuffer,
    decoder: CharsetDecoder,
    charBuffer: CharBuffer,
    buffer: ByteBuffer
): StringBuilder {
    decoder.reset()
    do {
        buffer.clear()
        readUntilDelimiter(delimiter, buffer)
        buffer.flip()

        if (!buffer.hasRemaining()) {
            // EOF of delimiter encountered
            //for (n in 0 until delimiter.remaining()) readByte()
            skipDelimiter(delimiter)

            charBuffer.clear()
            decoder.decode(buffer, charBuffer, true)
            charBuffer.flip()
            out.append(charBuffer)
            break
        }

        // do something with a buffer
        while (buffer.hasRemaining()) {
            charBuffer.clear()
            decoder.decode(buffer, charBuffer, false)
            charBuffer.flip()
            out.append(charBuffer)
        }
    } while (true)
    return out
}

suspend fun ByteReadChannel.readUntil(delimiter: Byte, bufferSize: Int = 1024): ByteArray {
    return readUntil(
        out = ByteArrayOutputStream(),
        delimiter = ByteBuffer.wrap(byteArrayOf(delimiter)),
        bufferSize = bufferSize
    ).toByteArray()
}

// Allocation free version
suspend fun ByteReadChannel.readUntil(
    out: ByteArrayOutputStream,
    delimiter: ByteBuffer,
    bufferSize: Int = 1024
): ByteArrayOutputStream {
    out.reset()
    val temp = ByteArray(bufferSize)
    val buffer = ByteBuffer.allocate(bufferSize)
    do {
        buffer.clear()
        readUntilDelimiter(delimiter, buffer)
        buffer.flip()

        if (!buffer.hasRemaining()) {
            skipDelimiter(delimiter)

            // EOF of delimiter encountered
            break
        }

        var pos = 0
        while (buffer.hasRemaining()) {
            val rem = buffer.remaining()
            buffer.get(temp, pos, rem)
            pos += rem
        }
        out.write(temp, 0, pos)
    } while (true)
    return out
}
