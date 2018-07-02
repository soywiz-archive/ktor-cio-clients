package io.ktor.experimental.client.redis.protocol

import io.ktor.experimental.client.redis.*
import kotlinx.coroutines.experimental.io.ByteBuffer

/**
 * Redis Serialization Protocol: https://redis.io/topics/protocol
 */

internal const val LF = '\n'.toByte()
internal val LF_BB = ByteBuffer.wrap(byteArrayOf(LF))
internal val tempCRLF = ByteArray(2)

internal fun BlobBuilder.appendEol() = append('\r').append('\n')

class RedisResponseException(message: String) : Exception(message)

