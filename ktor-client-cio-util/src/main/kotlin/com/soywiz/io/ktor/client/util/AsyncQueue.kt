package com.soywiz.io.ktor.client.util

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

class AsyncQueue {
    private var running = AtomicBoolean(false)
    private var queue = ConcurrentLinkedQueue<suspend () -> Unit>()

    suspend operator fun <T> invoke(func: suspend () -> T): T {
        val deferred = Deferred<T>()

        queue.add {
            val result = try {
                func()
            } catch (e: Throwable) {
                return@add deferred.reject(e)
            }
            deferred.resolve(result)
        }
        if (running.compareAndSet(false, true)) {
            runTasks(coroutineContext)
        }
        return deferred.await()
    }

    private fun runTasks(baseContext: CoroutineContext) {
        if (queue.isNotEmpty()) {
            val item = queue.remove()
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
