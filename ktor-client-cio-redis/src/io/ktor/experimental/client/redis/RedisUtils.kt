package io.ktor.experimental.client.redis

import kotlinx.io.pool.*
import java.nio.*


@Suppress("UNCHECKED_CAST")
suspend fun Redis.commandArrayString(vararg args: Any?): List<String> =
    (execute(*args) as List<String>?) ?: listOf()

suspend fun Redis.commandArrayLong(vararg args: Any?): List<Long> =
    (execute(*args) as List<Long>?) ?: listOf()

suspend fun Redis.commandString(vararg args: Any?): String? = execute(*args).toString()
suspend fun Redis.commandLong(vararg args: Any?): Long = execute(*args).toString().toLongOrNull() ?: 0L
suspend fun Redis.commandUnit(vararg args: Any?): Unit = run { execute(*args) }

internal const val DEFAULT_REDIS_BUFFER_SIZE = 4096

private const val DEFAULT_REDIS_POOL_CAPACITY = 1024

internal object RedisBufferPool : DefaultPool<ByteBuffer>(DEFAULT_REDIS_POOL_CAPACITY) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_REDIS_BUFFER_SIZE)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}

internal object RedisCharBufferPool : DefaultPool<CharBuffer>(DEFAULT_REDIS_POOL_CAPACITY) {
    override fun produceInstance(): CharBuffer = CharBuffer.allocate(DEFAULT_REDIS_BUFFER_SIZE)
    override fun clearInstance(instance: CharBuffer): CharBuffer = instance.apply { clear() }
}
