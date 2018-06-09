package io.ktor.experimental.client.util

import java.io.*
import java.util.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

interface Consumer<T> : Closeable {
    suspend fun consume(cancel: Signal<Unit>? = null): T?
}

interface Producer<T> : Closeable {
    fun produce(v: T): Unit
}

open class ProduceConsumer<T> : Consumer<T>, Producer<T> {
    private val items = LinkedList<T?>()
    private val consumers = LinkedList<(T?) -> Unit>()
    private var closed = false

    val availableCount get() = synchronized(this) { items.size }

    override fun produce(v: T) {
        synchronized(this) { items.addLast(v) }
        flush()
    }

    override fun close() {
        synchronized(this) {
            items.addLast(null)
            closed = true
        }
        flush()
    }

    private fun flush() {
        while (true) {
            var done = false
            var consumer: ((T?) -> Unit)? = null
            var item: T? = null
            synchronized(this) {
                if (consumers.isNotEmpty() && items.isNotEmpty()) {
                    consumer = consumers.removeFirst()
                    item = items.removeFirst()
                } else {
                    done = true
                }
            }
            if (done) break
            consumer!!(item)
        }
    }

    override suspend fun consume(cancel: Signal<Unit>?): T? = suspendCoroutine { c ->
        val consumer: (T?) -> Unit = {
            c.resume(it)
            //if (it != null) c.resume(it) else c.resumeWithException(EOFException())
        }
        if (cancel != null) {
            cancel {
                synchronized(this) {
                    consumers -= consumer
                }
                c.resumeWithException(CancellationException(""))
            }
        }
        synchronized(this) {
            consumers += consumer
        }
        flush()
    }
}
