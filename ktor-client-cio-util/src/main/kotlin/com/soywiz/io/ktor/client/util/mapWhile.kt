package com.soywiz.io.ktor.client.util

inline fun <T> mapWhile(cond: () -> Boolean, generator: () -> T): List<T> {
    val out = arrayListOf<T>()
    while (cond()) out += generator()
    return out
}
