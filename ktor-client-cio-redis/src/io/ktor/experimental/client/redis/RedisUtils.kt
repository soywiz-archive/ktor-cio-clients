package io.ktor.experimental.client.redis


@Suppress("UNCHECKED_CAST")
suspend fun Redis.commandArrayString(vararg args: Any?): List<String> =
    (execute(*args) as List<String>?) ?: listOf()

suspend fun Redis.commandArrayLong(vararg args: Any?): List<Long> =
    (execute(*args) as List<Long>?) ?: listOf()

suspend fun Redis.commandString(vararg args: Any?): String? = execute(*args).toString()
suspend fun Redis.commandLong(vararg args: Any?): Long = execute(*args).toString().toLongOrNull() ?: 0L
suspend fun Redis.commandUnit(vararg args: Any?): Unit = run { execute(*args) }
