package io.ktor.experimental.client.redis.protocol

import io.ktor.cio.*
import io.ktor.experimental.client.redis.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.charset.*

/**
 * Writer for the RESP protocol.
 */
class Writer(val charset: Charset, val forceBulk: Boolean = true) {
    private val blobBuilders: DefaultPool<BlobBuilder> = object : DefaultPool<BlobBuilder>(2048) {
        override fun produceInstance(): BlobBuilder =
            BlobBuilder(1024, charset)

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
            is List<*> -> {
                out.append('*').append(value.size).appendEol()
                for (item in value) writeValue(item, out)
            }
            is Array<*> -> {
                out.append('*').append(value.size).appendEol()
                for (item in value) writeValue(item, out)
            }
            else -> {
                if (forceBulk) {
                    blobBuilders.use { chunk ->
                        chunk.append("$value")
                        out.append('$').append(chunk.size()).appendEol()
                        out.append(chunk).appendEol()
                    }
                } else {
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
                            if (value.contains('\n') || value.contains('\r')) {
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
                            out.append('-').append((value.message ?: "Error").replace("\r", "").replace("\n", ""))
                                .appendEol()
                        }
                        else -> {
                            error("Unsupported $value to write")
                        }
                    }
                }
            }
        }
    }
}