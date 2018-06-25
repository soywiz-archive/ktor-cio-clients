package io.ktor.experimental.client.util

interface ObjectFactory<T> {
    fun create(): T
}

fun <T> ObjectFactory(block: () -> T): ObjectFactory<T> = object : ObjectFactory<T> {
    override fun create(): T = block()
}