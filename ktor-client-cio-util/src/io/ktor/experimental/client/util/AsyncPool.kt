package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.channels.*
import java.util.concurrent.atomic.*

const val ASYNC_POOL_DEFAULT_CAPACITY = 4096

class AsyncPool<T : Any>(
    val capacity: Int = ASYNC_POOL_DEFAULT_CAPACITY,
    private val factory: ObjectFactory<T>
) {
    private val items: Channel<T> = Channel()

    private val created = AtomicInteger(0)
    private val closed = AtomicBoolean(false)

    suspend fun borrow(): T {
        if (closed.get()) error("Pool closed")

        items.receiveOrNull()?.let { return it }

        while (true) {
            val current = created.get()
            if (current >= capacity) break

            if (!created.compareAndSet(current, current + 1)) continue

            return factory.create()
        }

        return items.receive()
    }

    fun dispose(item: T) {
        if (!items.offer(item)) throw PoolOverflowException(item)
    }
}

class PoolOverflowException(item: Any) : IllegalStateException("Failed to return item to pool: $item")

suspend fun <T : Any, R> AsyncPool<T>.use(callback: suspend (T) -> R): R {
    val item = borrow()
    try {
        return callback(item)
    } finally {
        dispose(item)
    }
}
