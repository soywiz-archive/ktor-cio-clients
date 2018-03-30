package com.soywiz.io.ktor.client.db.map

fun <T> AUTO(type: Class<T>): T {
    return when (type) {
        java.lang.Integer::class.java -> 0 as T
        java.lang.Long::class.java -> 0L as T
        else -> TODO("Unsupported type $type as autoincremental value")
    }
}

annotation class Primary
annotation class Unique
annotation class Name(val name: String)

inline fun <reified T> AUTO(): T = AUTO(T::class.java)
