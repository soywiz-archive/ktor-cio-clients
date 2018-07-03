package io.ktor.experimental.client.redis.protocol

import io.ktor.cio.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteBuffer
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*
import java.nio.charset.*

/**
 * Reader for the RESP protocol.
 */
internal class Reader(val charset: Charset, val bufferSize: Int = 1024) {

    private inner class State {
        val charsetDecoder = charset.newDecoder()
        val valueSB = StringBuilder(bufferSize)
        val valueCB = CharBuffer.allocate(bufferSize)
        val valueBB = ByteBuffer.allocate((bufferSize / charsetDecoder.maxCharsPerByte()).toInt())
        fun reset() {
            valueSB.setLength(0)
        }
    }

    private val states: DefaultPool<State> = object : DefaultPool<State>(2048) {
        override fun produceInstance(): State = State()
        override fun clearInstance(instance: State): State = instance.apply { reset() }
    }

    suspend fun readValue(reader: ByteReadChannel): Any? {
        val line = states.use { state ->
            reader.readUntilString(
                state.valueSB,
                LF_BB, state.charsetDecoder, state.valueCB, state.valueBB
            ).trimEnd().toString()
        }

        if (line.isEmpty()) throw RedisResponseException("Empty value")

        return when (line[0]) {
            '+' -> line.substring(1) // Simple String reply
            '-' -> throw RedisResponseException(line.substring(1)) // Error reply
            ':' -> line.substring(1).toLong() // Integer reply
            '$' -> { // Bulk replies
                val bytesToRead = line.substring(1).toInt()
                if (bytesToRead == -1) {
                    null
                } else {
                    val data = reader.readPacket(bytesToRead).readBytes()
                    reader.readShort() // Skip CRLF
                    data.toString(charset)
                }
            }
            '*' -> { // Array reply
                val arraySize = line.substring(1).toInt()
                (0 until arraySize).map { readValue(reader) }
            }
            else -> throw RedisResponseException("Unknown param type '${line[0]}'")
        }
    }

    private suspend fun ByteReadChannel.readUntilString(
        out: StringBuilder,
        delimiter: kotlinx.coroutines.experimental.io.ByteBuffer,
        decoder: CharsetDecoder,
        charBuffer: CharBuffer,
        buffer: kotlinx.coroutines.experimental.io.ByteBuffer
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
}
