package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.*
import java.util.*
import java.util.concurrent.atomic.*

class AsyncPool<T>(val maxItems: Int = Int.MAX_VALUE, val create: suspend (index: Int) -> T) {
    var createdItems = AtomicInteger()
    private val freedItem = LinkedList<T>()
    private val waiters = LinkedList<CompletableDeferred<Unit>>()
    val availableFreed: Int get() = synchronized(freedItem) { freedItem.size }

    suspend fun <TR> tempAlloc(callback: suspend (T) -> TR): TR {
        val item = alloc()
        try {
            return callback(item)
        } finally {
            free(item)
        }
    }

    suspend fun alloc(): T {
        while (true) {
            // If we have an available item just retrieve it
            synchronized(freedItem) {
                if (freedItem.isNotEmpty()) {
                    val item = freedItem.remove()
                    if (item != null) {
                        return item
                    }
                }
            }

            // If we don't have an available item yet and we can create more, just create one
            if (createdItems.get() < maxItems) {
                val index = createdItems.getAndAdd(1)
                return create(index)
            }
            // If we shouldn't create more items and we don't have more, just await for one to be freed.
            else {
                val deferred = CompletableDeferred<Unit>()
                synchronized(waiters) {
                    waiters += deferred
                }
                deferred.await()
            }
        }
    }

    fun free(item: T) {
        synchronized(freedItem) {
            freedItem.add(item)
        }
        val waiter = synchronized(waiters) { if (waiters.isNotEmpty()) waiters.remove() else null }
        waiter?.complete(Unit)
    }
}
