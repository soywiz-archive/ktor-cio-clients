package com.soywiz.io.ktor.client.redis

import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteBuffer
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.*
import java.nio.charset.*

/**
 * REdis Serialization Protocol: https://redis.io/topics/protocol
 */
object RESP {
    private const val LF = '\n'.toByte()
    private val LF_BB = ByteBuffer.wrap(byteArrayOf(LF))
    private val tempCRLF = ByteArray(2)

    /**
     * Reader for the RESP protocol.
     */
    class Reader(val charset: Charset, val bufferSize: Int = 1024) {
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
                reader.readUntilString(state.valueSB, LF_BB, state.charsetDecoder, state.valueCB, state.valueBB).trimEnd().toString()
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
                        reader.readFully(tempCRLF) // CR LF
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


        // Allocation free version
        private suspend fun ByteReadChannel.readUntilString(
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
    }

    /**
     * Writer for the RESP protocol.
     */
    class Writer(val charset: Charset, val forceBulk: Boolean = true) {
        private val blobBuilders: DefaultPool<BlobBuilder> = object : DefaultPool<BlobBuilder>(2048) {
            override fun produceInstance(): BlobBuilder = BlobBuilder(1024, charset)
            override fun clearInstance(instance: BlobBuilder): BlobBuilder = instance.apply { reset() }
        }

        fun buildValue(value: Any?, temp: ByteArrayOutputStream = ByteArrayOutputStream()): ByteArray {
            temp.reset()
            writeValue(value, temp)
            return temp.toByteArray()
        }

        fun writeValue(value: Any?, out: OutputStream) {
            blobBuilders.use { cmd ->
                writeValue(value, cmd)
                cmd.writeTo(out)
            }
        }

        suspend fun writeValue(value: Any?, out: ByteWriteChannel) {
            blobBuilders.use { cmd ->
                writeValue(value, cmd)
                cmd.writeTo(out)
            }
        }

        private fun writeValue(value: Any?, out: BlobBuilder) {
            when (value) {
                null -> out.append("+(nil)").appendEol()
                is Int -> out.append(':').append(value).appendEol()
                is Long -> out.append(':').append(value).appendEol()
                is ByteArray -> {
                    blobBuilders.use { chunk ->
                        chunk.write(value)
                        out.append('$').append(chunk.size()).appendEol()
                        out.append(chunk).appendEol()
                    }
                }
                is String -> {
                    if (forceBulk || value.contains('\n') || value.contains('\r')) {
                        blobBuilders.use { chunk ->
                            chunk.append(value)
                            out.append('$').append(chunk.size()).appendEol()
                            out.append(chunk).appendEol()
                        }
                    } else {
                        out.append('+').append(value).appendEol()
                    }
                }
                is Throwable -> {
                    out.append('-').append((value.message ?: "Error").replace("\r", "").replace("\n", "")).appendEol()
                }
                is List<*> -> {
                    out.append('*').append(value.size).appendEol()
                    for (item in value) writeValue(item, out)
                }
                is Array<*> -> {
                    out.append('*').append(value.size).appendEol()
                    for (item in value) writeValue(item, out)
                }
                else -> {
                    error("Unsupported $value to write")
                }
            }
        }

        private fun BlobBuilder.appendEol() = append('\r').append('\n')
    }
}

class RedisResponseException(message: String) : Exception(message)

private inline fun <T : Any, R> ObjectPool<T>.use(block: (T) -> R): R {
    val item = borrow()
    try {
        return block(item)
    } finally {
        recycle(item)
    }
}
