package com.soywiz.io.ktor.client.util

import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

class AsyncQueue {
    var running = AtomicBoolean(false); private set
    private var queue = LinkedList<suspend () -> Unit>()

    val queued get() = synchronized(queue) { queue.size }

    suspend operator fun <T> invoke(func: suspend () -> T): T {
        val deferred = Deferred<T>()

        synchronized(queue) {
            queue.add {
                val result = try {
                    func()
                } catch (e: Throwable) {
                    return@add deferred.reject(e)
                }
                deferred.resolve(result)
            }
        }
        if (running.compareAndSet(false, true)) {
            runTasks(coroutineContext)
        }
        return deferred.await()
    }

    private fun runTasks(baseContext: CoroutineContext) {
        val item = synchronized(queue) { if (queue.isNotEmpty()) queue.remove() else null }
        if (item != null) {
            item.startCoroutine(object : Continuation<Unit> {
                override val context: CoroutineContext = baseContext
                override fun resume(value: Unit) = runTasks(baseContext)
                override fun resumeWithException(exception: Throwable) = runTasks(baseContext)
            })
        } else {
            running.set(false)
        }
    }
}
