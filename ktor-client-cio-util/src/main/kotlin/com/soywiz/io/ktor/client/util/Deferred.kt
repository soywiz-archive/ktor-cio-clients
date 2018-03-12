package com.soywiz.io.ktor.client.util

import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class Deferred<T> {
    var resolved = false
    var value: T? = null
    var exception: Throwable? = null
    private var continuations = ConcurrentLinkedQueue<Continuation<T>>()

    fun resolve(value: T) {
        this.value = value
        this.resolved = true
        flush()
    }

    fun reject(exception: Throwable) {
        this.exception = exception
        this.resolved = true
        flush()
    }

    suspend fun await(): T = suspendCoroutine { c ->
        continuations.add(c)
        flush()
    }

    private fun flush() {
        if (resolved) {
            while (continuations.isNotEmpty()) {
                val c = continuations.remove() ?: break
                if (exception != null) {
                    c.resumeWithException(exception!!)
                } else {
                    c.resume(value as T)
                }
            }
        }
    }
}
