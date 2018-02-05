package com.soywiz.io.ktor.client.redis

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.coroutines.experimental.suspendCoroutine

//private const val DEBUG = true
private const val DEBUG = false

// https://redis.io/topics/protocol
class Redis(val maxConnections: Int = 50, val stats: Stats = Stats(), private val clientFactory: suspend () -> Client) :
    RedisCommand {
    companion object {
        suspend operator fun invoke(
            hosts: List<String> = listOf("127.0.0.1:6379"),
            maxConnections: Int = 50,
            charset: Charset = Charsets.UTF_8,
            password: String? = null,
            stats: Stats = Stats(),
            bufferSize: Int = 0x1000
        ): Redis {
            val hostsWithPorts = hosts.map { HostWithPort.parse(it, 6379) }

            var index: Int = 0

            return Redis(maxConnections, stats) {
                val tcpClient = AsyncClient(bufferSize)
                //Console.log("tcpClient: ${tcpClient::class}")
                Client(
                    reader = tcpClient,
                    reconnect = { client ->
                        index = (index + 1) % hostsWithPorts.size
                        val host = hostsWithPorts[index] // Round Robin
                        tcpClient.connect(host.host, host.port)
                        if (password != null) client.auth(password)
                    },
                    writer = tcpClient,
                    closeable = tcpClient,
                    charset = charset,
                    stats = stats
                ).apply {
                    init()
                }
            }
        }

        //private const val CR = '\r'.toByte()
        private const val LF = '\n'.toByte()
    }

    class Stats {
        val commandsQueued = AtomicLong()
        val commandsStarted = AtomicLong()
        val commandsPreWritten = AtomicLong()
        val commandsWritten = AtomicLong()
        val commandsErrored = AtomicLong()
        val commandsFinished = AtomicLong()

        override fun toString(): String {
            return "Stats(commandsQueued=$commandsQueued, commandsStarted=$commandsStarted, commandsPreWritten=$commandsPreWritten, commandsWritten=$commandsWritten, commandsErrored=$commandsErrored, commandsFinished=$commandsFinished)"
        }
    }

    class Client(
        val reader: AsyncInputStream,
        val writer: AsyncOutputStream,
        val closeable: AsyncCloseable,
        val charset: Charset = Charsets.UTF_8,
        val stats: Stats = Stats(),
        val reconnect: suspend (Client) -> Unit = {}
    ) : RedisCommand {
        suspend fun close() = this.closeable.close()

        private val commandQueue = AsyncQueue()

        internal suspend fun init() {
            commandQueue {
                try {
                    reconnect(this@Client)
                } catch (e: IOException) {
                }
            }
        }

        private suspend fun readValue(): Any? {
            val line = reader.readUntil(LF).toString(charset).trim()
            debug { "Redis[RECV]: $line" }
            //val line = reader.readLine(charset = charset).trim()
            //println(line)

            if (line.isEmpty()) throw ResponseException("Empty value")
            return when (line[0]) {
                '+' -> line.substring(1) // Status reply
                '-' -> throw ResponseException(line.substring(1)) // Error reply
                ':' -> line.substring(1).toLong() // Integer reply
                '$' -> { // Bulk replies
                    val bytesToRead = line.substring(1).toInt()
                    if (bytesToRead == -1) {
                        null
                    } else {
                        val data = reader.readBytesExact(bytesToRead)
                        reader.skip(2) // CR LF
                        data.toString(charset).apply {
                            debug { "Redis[RECV][data]: $this" }
                        }
                    }
                }
                '*' -> { // Array reply
                    val arraySize = line.substring(1).toLong()
                    (0 until arraySize).map { readValue() }
                }
                else -> throw ResponseException("Unknown param type '${line[0]}'")
            }
        }

        val maxRetries = 10

        inline private fun debug(msg: () -> String) {
            if (DEBUG) println(msg())
        }

        override suspend fun commandAny(vararg args: Any?): Any? {
            //println(args.toList())
            stats.commandsQueued.incrementAndGet()
            return commandQueue {
                val cmd = StringBuilder()
                cmd.append('*')
                cmd.append(args.size)
                cmd.append("\r\n")
                for (arg in args) {
                    //val sarg = "$arg".redisQuoteIfRequired()
                    val sarg = "$arg"
                    // Length of the argument.
                    val size = sarg.toByteArray(charset).size
                    cmd.append('$')
                    cmd.append(size)
                    cmd.append("\r\n")
                    cmd.append(sarg)
                    cmd.append("\r\n")
                }

                // Common queue is not required align reading because Redis support pipelining : https://redis.io/topics/pipelining
                val dataString = cmd.toString()
                val data = dataString.toByteArray(charset)
                var retryCount = 0

                debug { "Redis[SEND]: $dataString" }

                retry@ while (true) {
                    stats.commandsStarted.incrementAndGet()
                    try {
                        stats.commandsPreWritten.incrementAndGet()
                        //Console.log("writer: $writer")
                        //Console.log("writer: ${writer::class}")
                        //Console.log(writer)
                        //println("[a]")
                        writer.writeFully(data) // @TODO: Maybe use writeAvailable instead of appending?
                        writer.flush()
                        //println("[b]")
                        stats.commandsWritten.incrementAndGet()
                        //println("[c]")
                        val res = readValue()
                        //println("[d]")
                        stats.commandsFinished.incrementAndGet()
                        return@commandQueue res
                    } catch (t: IOException) {
                        t.printStackTrace()
                        stats.commandsErrored.incrementAndGet()
                        try {
                            reconnect(this@Client)
                        } catch (e: Throwable) {
                        }
                        delay(500 * retryCount)
                        retryCount++
                        if (retryCount < maxRetries) {
                            continue@retry
                        } else {
                            throw RuntimeException("Giving up with this redis request max retries $maxRetries")
                        }
                    } catch (t: Throwable) {
                        stats.commandsErrored.incrementAndGet()
                        println(t)
                        throw t
                    }
                }
            }
        }
    }

    private val clientPool = AsyncPool(maxItems = maxConnections) { clientFactory() }

    override suspend fun commandAny(vararg args: Any?): Any? = clientPool.tempAlloc { it.commandAny(*args) }

    class ResponseException(message: String) : Exception(message)
}

interface RedisCommand {
    suspend fun commandAny(vararg args: Any?): Any?
}

@Suppress("UNCHECKED_CAST")
suspend fun RedisCommand.commandArray(vararg args: Any?): List<String> =
    (commandAny(*args) as List<String>?) ?: listOf()

suspend fun RedisCommand.commandString(vararg args: Any?): String? = commandAny(*args)?.toString()
suspend fun RedisCommand.commandLong(vararg args: Any?): Long = commandAny(*args)?.toString()?.toLongOrNull() ?: 0L
suspend fun RedisCommand.commandUnit(vararg args: Any?): Unit = run { commandAny(*args) }

///////////////////////////////////////////////
///////////////////////////////////////////////

interface AsyncCloseable {
    suspend fun close(): Unit
}

interface AsyncInputStream {
    //suspend fun readBytesUpTo(count: Int): ByteArray
    suspend fun read(): Int
    suspend fun skip(count: Int): Unit
    suspend fun readUntil(b: Byte): ByteArray
    suspend fun readBytesUpTo(buffer: ByteArray, offset: Int = 0, size: Int = buffer.size - offset): Int
}

fun AsyncInputStream.toBuffered(bufferSize: Int) = this // @TODO

interface AsyncOutputStream {
    suspend fun writeFully(buffer: ByteArray, offset: Int = 0, size: Int = buffer.size - offset): Unit
    suspend fun flush(): Unit
}

class AsyncClient(val bufferSize: Int = 0x1000) : AsyncInputStream, AsyncOutputStream, AsyncCloseable {
    private var sc = AsynchronousSocketChannel.open()
    private val temp = CircularByteArray(32 - Integer.numberOfLeadingZeros(bufferSize))
    val buffer = ByteBuffer.allocate(temp.writeAvailable)

    suspend fun connect(host: String, port: Int) = suspendCoroutine<Unit> { c ->
        //sc.close()
        sc.connect(InetSocketAddress(host, port), this, object : CompletionHandler<Void, AsyncClient> {
            override fun completed(result: Void?, attachment: AsyncClient) = c.resume(Unit)
            override fun failed(exc: Throwable, attachment: AsyncClient) = c.resumeWithException(exc)
        })
    }

    override suspend fun close() {
        sc.close()
    }

    override suspend fun read(): Int {
        fillBufferIfRequired()
        return if (temp.readAvailable > 0) (temp.get().toInt() and 0xFF) else -1
    }

    override suspend fun readUntil(b: Byte): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            if (temp.readAvailable < 1) {
                fillBufferIfRequired()
                if (temp.readAvailable < 1) break // end of stream
            }
            val c = temp.get()
            if (c == b) break
            out.write(c.toInt())
        }
        return out.toByteArray()
    }

    override suspend fun skip(count: Int): Unit {
        var pending = count
        while (pending > 0) {
            fillBufferIfRequired(pending)
            val chunk = Math.min(pending, temp.readAvailable)
            temp.skip(chunk)
            pending -= chunk
        }
        //readBytesExact(count)
    }

    override suspend fun readBytesUpTo(buffer: ByteArray, offset: Int, size: Int): Int {
        fillBufferIfRequired()
        val read = Math.min(temp.readAvailable, size)
        this.temp.get(buffer, offset, read)
        return read
    }

    suspend private fun fillBufferIfRequired(required: Int = 1) {
        if (temp.readAvailable < required) {
            buffer.clear()
            buffer.limit(temp.writeAvailable)
            return suspendCoroutine { c ->
                sc.read(buffer, this, object : CompletionHandler<Int, AsyncClient> {
                    override fun completed(result: Int, attachment: AsyncClient) = c.resume(temp.put(buffer.apply { flip() }, result))
                    override fun failed(exc: Throwable, attachment: AsyncClient) = c.resumeWithException(exc)
                })
            }
        }
    }

    override suspend fun writeFully(buffer: ByteArray, offset: Int, size: Int): Unit = suspendCoroutine { c ->
        sc.write(ByteBuffer.wrap(buffer, offset, size), this, object : CompletionHandler<Int, AsyncClient> {
            override fun completed(result: Int, attachment: AsyncClient) = c.resume(Unit)
            override fun failed(exc: Throwable, attachment: AsyncClient) = c.resumeWithException(exc)
        })
    }

    override suspend fun flush() = Unit // Already flushed
}

suspend fun AsyncInputStream.readBytesExact(count: Int): ByteArray {
    val out = ByteArray(count)
    var offset = 0
    var pending = out.size
    while (pending > 0) {
        val read = readBytesUpTo(out, offset, pending)
        if (read <= 0) break
        pending -= read
        offset += read
    }
    if (pending > 0) throw IOException("Couldn't read $count but just $offset")
    return out
}

class CircularByteArray(private val capacityBits: Int) {
    private val capacity = (1 shl capacityBits)
    private val mask = capacity - 1
    private val data = ByteArray(capacity)
    private var readPos: Int = 0
    private var writePos: Int = 0
    val readAvailable: Int get() = if (writePos >= readPos) writePos - readPos else capacity + (writePos - readPos)
    //val readAvailable: Int get() = writePos - readPos
    val writeAvailable: Int get() = capacity - readAvailable

    fun skip(count: Int) {
        if (readAvailable < count) throw IOException("Buffer is empty")
        readPos = (readPos + count) and mask
    }

    fun get(): Byte {
        if (readAvailable < 1) throw IOException("Buffer is empty")
        return data[readPos++ and mask].apply {
            readPos = readPos and mask
        }
    }

    fun get(v: ByteArray, offset: Int = 0, size: Int = v.size - offset) {
        if (readAvailable < size) throw IOException("Buffer is empty")
        // @TODO: Up to two arraycopy
        for (n in 0 until size) v[offset + n] = data[readPos++ and mask]
        readPos = readPos and mask
    }

    fun put(v: Byte) {
        if (writeAvailable < 1) throw IOException("Buffer is full")
        data[writePos++ and mask] = v
        writePos = writePos and mask
    }

    fun put(v: ByteArray, offset: Int = 0, size: Int = v.size - offset) {
        if (writeAvailable < size) throw IOException("Buffer is full")
        // @TODO: Up to two arraycopy
        for (n in 0 until size) data[writePos++ and mask] = v[offset + n]
        writePos = writePos and mask
    }

    fun put(v: ByteBuffer, size: Int) {
        if (writeAvailable < size) throw IOException("Buffer is full")
        // @TODO: Up to two get
        for (n in 0 until size) data[writePos++ and mask] = v.get()
        writePos = writePos and mask
    }
}

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

    suspend fun wait(): T = suspendCoroutine { c ->
        continuations.add(c)
        flush()
    }

    private fun flush() {
        if (resolved) {
            while (true) {
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
            runTasks(getCoroutineContext())
        }
        return deferred.wait()
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

class AsyncPool<T>(val maxItems: Int = Int.MAX_VALUE, val create: suspend () -> T) {
    var createdItems = AtomicInteger()
    private val freedItem = LinkedList<T>()

    suspend fun <TR> tempAlloc(callback: suspend (T) -> TR): TR {
        val item = alloc()
        try {
            return callback(item)
        } finally {
            free(item)
        }
    }

    suspend fun alloc(): T {
        return if (createdItems.get() >= maxItems) {
            freedItem.remove()!!
        } else {
            createdItems.addAndGet(1)
            create()
        }
    }

    fun free(item: T) {
        freedItem.add(item)
    }
}

data class HostWithPort(val host: String, val port: Int) {
    companion object {
        fun parse(str: String, defaultPort: Int): HostWithPort {
            val parts = str.split(':', limit = 2)
            return HostWithPort(parts[0], parts.getOrElse(1) { "$defaultPort" }.toInt())
        }
    }
}

suspend fun getCoroutineContext(): CoroutineContext = suspendCoroutine<CoroutineContext> { c ->
    c.resume(c.context)
}

internal object RedisExperiment {
    @JvmStatic
    fun main(args: Array<String>): Unit {
        runBlocking {
            val redis = Redis()
            redis.set("a", "hello")
            println(redis.get("a"))
        }
    }
}

// @TODO: SLOWER:
//val cmd = ByteArrayOutputStream()
//val ps = PrintStream(cmd, true, Charsets.UTF_8.name())
//
//ps.print('*')
//ps.print(args.size)
//ps.print("\r\n")
//for (arg in args) {
//	val data = "$arg".toByteArray(charset)
//	ps.print('$')
//	ps.print(data.size)
//	ps.print("\r\n")
//	ps.write(data)
//	ps.print("\r\n")
//}
//
//// Common queue is not required align reading because Redis support pipelining : https://redis.io/topics/pipelining
//return commandQueue {
//	writer.writeBytes(cmd.toByteArray())
//	readValue()
//}
