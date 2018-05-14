package com.soywiz.io.ktor.client.util

interface SuspendingIterator<out T> {
    suspend operator fun hasNext(): Boolean
    suspend operator fun next(): T
}

interface SuspendingSequence<out T> {
    suspend operator fun iterator(): SuspendingIterator<T>
}

suspend fun <T> SuspendingSequence<T>.first() = this.iterator().next()
suspend fun <T> SuspendingSequence<T>.firstOrNull(): T? {
    val it = iterator()
    return if (it.hasNext()) it.next() else null
}

suspend fun <T> SuspendingSequence<T>.toList() = this.iterator().toList()
suspend fun <T> SuspendingIterator<T>.toList(): List<T> {
    val out = arrayListOf<T>()
    while (this.hasNext()) out += this.next()
    return out
}

fun <T> List<T>.toSuspendingSequence() = object : SuspendingSequence<T> {
    override suspend fun iterator(): SuspendingIterator<T> {
        val lit = this@toSuspendingSequence.iterator()
        return object : SuspendingIterator<T> {
            override suspend fun hasNext(): Boolean = lit.hasNext()
            override suspend fun next(): T = lit.next()
        }
    }
}

suspend fun <T, R> SuspendingSequence<T>.map(transform: (T) -> R): SuspendingSequence<R> {
    return object : SuspendingSequence<R> {
        override suspend fun iterator(): SuspendingIterator<R> {
            val iter = this@map.iterator()
            return object : SuspendingIterator<R> {
                override suspend fun hasNext(): Boolean = iter.hasNext()
                override suspend fun next(): R = transform(iter.next())
            }
        }
    }
}


/*
interface SuspendingGenerator<T> {
    fun yield(value: T)
}

suspend fun <T> generateSuspendingSequence(callback: suspend SuspendingGenerator<T>.() -> Unit): SuspendingSequence<T> {
    val obj = object : SuspendingGenerator<T>, SuspendingSequence<T> {
        var closed = false

        override fun yield(value: T) {
        }

        fun close() {
            closed = true
        }

        override fun iterator(): SuspendingIterator<T> {
            return object : SuspendingIterator<T> {
                override suspend fun hasNext(): Boolean = !closed
                override suspend fun next(): T {
                }
            }
        }
    }

    try {
        callback(obj)
    } finally {
        obj.close()
    }

    return obj
}

suspend fun <T, R> SuspendingSequence<T>.map(transform: (T) -> R): SuspendingSequence<R> = generateSuspendingSequence {
    for (item in this@map) {
        yield(transform(item))
    }
}

suspend fun <T> SuspendingSequence<T>.filter(filter: (T) -> Boolean): SuspendingSequence<T> = generateSuspendingSequence {
    for (item in this@filter) {
        if (filter(item)) yield(item)
    }
}
*/
