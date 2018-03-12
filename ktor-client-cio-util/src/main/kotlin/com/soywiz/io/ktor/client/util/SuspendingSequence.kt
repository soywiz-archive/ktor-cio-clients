package com.soywiz.io.ktor.client.util

interface SuspendingIterator<out T> {
    suspend operator fun hasNext(): Boolean
    suspend operator fun next(): T
}

interface SuspendingSequence<out T> {
    operator fun iterator(): SuspendingIterator<T>
}

suspend fun <T> SuspendingSequence<T>.first() = this.iterator().next()
suspend fun <T> SuspendingSequence<T>.toList() = this.iterator().toList()
suspend fun <T> SuspendingIterator<T>.toList(): List<T> {
    val out = arrayListOf<T>()
    while (this.hasNext()) out += this.next()
    return out
}

fun <T> List<T>.toSuspendingSequence() = object : SuspendingSequence<T> {
    override fun iterator(): SuspendingIterator<T> {
        val lit = this@toSuspendingSequence.iterator()
        return object : SuspendingIterator<T> {
            override suspend fun hasNext(): Boolean = lit.hasNext()
            override suspend fun next(): T = lit.next()
        }
    }
}
