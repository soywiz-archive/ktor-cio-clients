package com.soywiz.io.ktor.client.redis

import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.nio.charset.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

/**
 * A Redis basic interface exposing emiting commands receiving their responses.
 *
 * Specific commands are exposed as extension methods.
 */
interface Redis {
    val charset: Charset get() = Charsets.UTF_8

    /**
     * Executes a raw command. Each [args] will be sent as a String.
     *
     * It returns a type depending on the command.
     * The returned value can be of type [String], [Long] or [List].
     *
     * It may throw a [RedisResponseException]
     */
    suspend fun commandAny(vararg args: Any?): Any?

    suspend fun capturePipes(): Redis.Pipes = TODO("This client doesn't implement redis subscription")

    data class Pipes(
        val reader: ByteReadChannel,
        val writer: ByteWriteChannel,
        val closeable: Closeable
    )
}

/**
 * Constructs a Redis multi-client that will connect to [addresses] in a round-robin fashion keeping a connection pool,
 * keeping as much as [maxConnections] and using the [charset].
 * Optionally you can define the [password] of the connection.
 * You can specify a [stats] object that will be populated by the clients.
 */
fun Redis(
    addresses: List<SocketAddress> = listOf(InetSocketAddress("127.0.0.1", 6379)),
    maxConnections: Int = 50,
    charset: Charset = Charsets.UTF_8,
    password: String? = null,
    stats: RedisStats = RedisStats(),
    bufferSize: Int = 0x1000
): Redis {
    var index = 0
    return RedisCluster(maxConnections = maxConnections, charset = charset) {
        val tcpClientFactory = aSocket().tcp()
        RedisClient(
            reconnect = { client ->
                index = (index + 1) % addresses.size
                val host = addresses[index] // Round Robin
                val socket = tcpClientFactory.connect(host)
                if (password != null) client.auth(password)
                Redis.Pipes(socket.openReadChannel(), socket.openWriteChannel(autoFlush = true), socket)
            },
            bufferSize = bufferSize,
            charset = charset,
            stats = stats
        )
    }
}

data class RedisStats(
    val commandsQueued: AtomicLong = AtomicLong(),
    val commandsStarted: AtomicLong = AtomicLong(),
    val commandsPreWritten: AtomicLong = AtomicLong(),
    val commandsWritten: AtomicLong = AtomicLong(),
    val commandsErrored: AtomicLong = AtomicLong(),
    val commandsFailed: AtomicLong = AtomicLong(),
    val commandsFinished: AtomicLong = AtomicLong()
)

/**
 * Redis client implementing the redis wire protocol defined in https://redis.io/topics/protocol
 */
internal class RedisCluster(
    internal val maxConnections: Int = 50,
    override val charset: Charset = Charsets.UTF_8,
    internal val clientFactory: suspend () -> RedisClient
) : Redis {
    private val clientPool = AsyncPool(maxItems = maxConnections) { clientFactory() }

    override suspend fun commandAny(vararg args: Any?): Any? = clientPool.tempAlloc { it.commandAny(*args) }

    override suspend fun capturePipes(): Redis.Pipes {
        // Creates a new client for the subscription instead of reusing one,
        // since it will enter in a subscription state
        return clientFactory().capturePipes()
    }
}

internal class RedisClient(
    override val charset: Charset = Charsets.UTF_8,
    private val stats: RedisStats = RedisStats(),
    private val bufferSize: Int = 0x1000,
    private val reconnect: suspend (RedisClient) -> Redis.Pipes
) : Redis {
    companion object {
        val MAX_RETRIES = 10
    }

    lateinit var _pipes: Redis.Pipes
    private val initOnce = OnceAsync()
    private val commandQueue = AsyncTaskQueue()
    private val respReader = RESP.Reader(charset, bufferSize)
    private val respBuilder = RESP.Writer(charset)
    private val cmd = BlobBuilder(bufferSize, charset)

    suspend fun reader(): ByteReadChannel = initOnce().reader
    suspend fun writer(): ByteWriteChannel = initOnce().writer
    suspend fun closeable(): Closeable = initOnce().closeable

    suspend fun reconnect() {
        try {
            _pipes = reconnect(this@RedisClient)
        } catch (e: Throwable) {
            println("Failed to connect, retrying... ${e.message}")
            throw e
        }
    }

    suspend fun reconnectRetrying() {
        var retryCount = 0
        retry@ while (true) {
            try {
                reconnect()
                return
            } catch (e: IOException) {
                delay(500 * retryCount)
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    continue@retry
                } else {
                    throw IOException("Giving up trying to connect to redis max retries: $MAX_RETRIES")
                }
            }
        }
    }

    private suspend fun initOnce(): Redis.Pipes {
        initOnce {
            commandQueue {
                reconnectRetrying()
            }
        }
        return _pipes
    }

    suspend fun close() = closeable().close()

    override suspend fun commandAny(vararg args: Any?): Any? {
        if (capturedPipes) error("Can't emit plain redis commands after entering into a redis sub-state")
        val writer = writer()
        stats.commandsQueued.incrementAndGet()
        return commandQueue {
            cmd.reset()
            respBuilder.writeValue(args, cmd)

            // Common queue is not required align reading because Redis support pipelining : https://redis.io/topics/pipelining
            var retryCount = 0

            retry@ while (true) {
                stats.commandsStarted.incrementAndGet()
                try {
                    stats.commandsPreWritten.incrementAndGet()

                    cmd.writeTo(writer)
                    writer.flush()

                    stats.commandsWritten.incrementAndGet()

                    val res = respReader.readValue(reader())

                    stats.commandsFinished.incrementAndGet()
                    return@commandQueue res
                } catch (t: IOException) {
                    t.printStackTrace()
                    stats.commandsErrored.incrementAndGet()
                    try {
                        reconnect()
                    } catch (e: Throwable) {
                    }
                    delay(500 * retryCount)
                    retryCount++
                    if (retryCount < MAX_RETRIES) {
                        continue@retry
                    } else {
                        throw IOException("Giving up with this redis request max retries $MAX_RETRIES")
                    }
                } catch (t: RedisResponseException) {
                    stats.commandsFailed.incrementAndGet()
                    throw t
                }
            }
        }
    }

    private var capturedPipes: Boolean = false
    override suspend fun capturePipes(): Redis.Pipes {
        capturedPipes = true
        return initOnce()
    }
}

@Suppress("UNCHECKED_CAST")
suspend fun Redis.commandArrayString(vararg args: Any?): List<String> =
    (commandAny(*args) as List<String>?) ?: listOf()

suspend fun Redis.commandArrayLong(vararg args: Any?): List<Long> =
    (commandAny(*args) as List<Long>?) ?: listOf()

suspend fun Redis.commandString(vararg args: Any?): String? = commandAny(*args)?.toString()
suspend fun Redis.commandLong(vararg args: Any?): Long = commandAny(*args)?.toString()?.toLongOrNull() ?: 0L
suspend fun Redis.commandUnit(vararg args: Any?): Unit = run { commandAny(*args) }

private class OnceAsync {
    var deferred: kotlinx.coroutines.experimental.Deferred<Unit>? = null

    suspend operator fun invoke(callback: suspend () -> Unit) {
        if (deferred == null) {
            deferred = async { callback() }
        }
        return deferred!!.await()
    }
}

private class AsyncTaskQueue {
    var running = AtomicBoolean(false); private set
    private var queue = LinkedList<suspend () -> Unit>()

    val queued get() = synchronized(queue) { queue.size }

    suspend operator fun <T> invoke(func: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()

        synchronized(queue) {
            queue.add {
                val result = try {
                    func()
                } catch (e: Throwable) {
                    deferred.completeExceptionally(e)
                    return@add
                }
                deferred.complete(result)
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

private class AsyncPool<T>(val maxItems: Int = Int.MAX_VALUE, val create: suspend (index: Int) -> T) {
    var createdItems = AtomicInteger()
    private val freedItem = LinkedList<T>()
    private val waiters = LinkedList<CompletableDeferred<Unit>>()
    val availableFreed: Int get() = synchronized(freedItem) { freedItem.size }

    suspend fun <TR> tempAlloc(callback: suspend (T) -> TR): TR {
        val item = alloc()
        try {
            return callback(item)
        } finally {
            free(item)
        }
    }

    suspend fun alloc(): T {
        while (true) {
            // If we have an available item just retrieve it
            synchronized(freedItem) {
                if (freedItem.isNotEmpty()) {
                    val item = freedItem.remove()
                    if (item != null) {
                        return item
                    }
                }
            }

            // If we don't have an available item yet and we can create more, just create one
            if (createdItems.get() < maxItems) {
                val index = createdItems.getAndAdd(1)
                return create(index)
            }
            // If we shouldn't create more items and we don't have more, just await for one to be freed.
            else {
                val deferred = CompletableDeferred<Unit>()
                synchronized(waiters) {
                    waiters += deferred
                }
                deferred.await()
            }
        }
    }

    fun free(item: T) {
        synchronized(freedItem) {
            freedItem.add(item)
        }
        val waiter = synchronized(waiters) { if (waiters.isNotEmpty()) waiters.remove() else null }
        waiter?.complete(Unit)
    }
}
