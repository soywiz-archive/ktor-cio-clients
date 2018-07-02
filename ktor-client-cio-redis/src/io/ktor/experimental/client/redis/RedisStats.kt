package io.ktor.experimental.client.redis

import java.util.concurrent.atomic.*

data class RedisStats(
    val commandsQueued: AtomicLong = AtomicLong(),
    val commandsStarted: AtomicLong = AtomicLong(),
    val commandsPreWritten: AtomicLong = AtomicLong(),
    val commandsWritten: AtomicLong = AtomicLong(),
    val commandsErrored: AtomicLong = AtomicLong(),
    val commandsFailed: AtomicLong = AtomicLong(),
    val commandsFinished: AtomicLong = AtomicLong()
)
