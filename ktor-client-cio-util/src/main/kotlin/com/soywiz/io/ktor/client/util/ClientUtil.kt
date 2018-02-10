package com.soywiz.io.ktor.client.util

import java.io.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.coroutines.experimental.suspendCoroutine

class Once {
    var completed = false

    inline operator fun invoke(callback: () -> Unit) {
        if (!completed) {
            completed = true
            callback()
        }
    }
}

interface AsyncCloseable {
    suspend fun close(): Unit
}

suspend inline fun <T : AsyncCloseable> T.use(callback: (T) -> Unit) {
    try {
        callback(this)
    } finally {
        this.close()
    }
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

class AsyncClient(val bufferSize: Int = 0x1000) : AsyncInputStream,
    AsyncOutputStream, AsyncCloseable {
    private var sc = AsynchronousSocketChannel.open()
    private val cb = CircularByteArray(32 - Integer.numberOfLeadingZeros(bufferSize))
    private val buffer = ByteBuffer.allocate(cb.writeAvailable)

    suspend fun connect(host: String, port: Int) = suspendCoroutine<AsyncClient> { c ->
        //sc.close()
        sc.connect(InetSocketAddress(host, port), this, object : CompletionHandler<Void, AsyncClient> {
            override fun completed(result: Void?, attachment: AsyncClient) = c.resume(this@AsyncClient)
            override fun failed(exc: Throwable, attachment: AsyncClient) = c.resumeWithException(exc)
        })
    }

    override suspend fun close() {
        sc.close()
    }

    override suspend fun read(): Int {
        fillBufferIfRequired()
        return if (cb.readAvailable > 0) (cb.get().toInt() and 0xFF) else -1
    }

    override suspend fun readUntil(b: Byte): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            if (cb.readAvailable < 1) {
                fillBufferIfRequired()
                if (cb.readAvailable < 1) break // end of stream
            }
            val c = cb.get()
            if (c == b) break
            out.write(c.toInt())
        }
        return out.toByteArray()
    }

    override suspend fun skip(count: Int): Unit {
        var pending = count
        while (pending > 0) {
            fillBufferIfRequired(pending)
            val chunk = Math.min(pending, cb.readAvailable)
            cb.skip(chunk)
            pending -= chunk
        }
        //readBytesExact(count)
    }

    override suspend fun readBytesUpTo(buffer: ByteArray, offset: Int, size: Int): Int {
        fillBufferIfRequired()
        val read = Math.min(cb.readAvailable, size)
        this.cb.get(buffer, offset, read)
        return read
    }

    suspend private fun fillBufferIfRequired(required: Int = 1) {
        if (cb.readAvailable < required) {
            buffer.clear()
            buffer.limit(cb.writeAvailable)
            return suspendCoroutine { c ->
                sc.read(buffer, this, object : CompletionHandler<Int, AsyncClient> {
                    override fun completed(result: Int, attachment: AsyncClient) =
                        c.resume(cb.put(buffer.apply { flip() }, result))

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
    init {
        if (capacityBits !in 1 until 30) throw IOException("Invalid capacityBits $capacityBits")
    }

    private val capacity = (1 shl (capacityBits - 1))
    private val mask = capacity - 1
    private val data = ByteArray(capacity)
    private var readPos: Int = 0
    private var writePos: Int = 0
    var readAvailable: Int = 0; private set
    val writeAvailable: Int get() = capacity - readAvailable

    fun skip(count: Int) {
        if (count > readAvailable) throw IOException("Buffer is empty")
        readPos = (readPos + count) and mask
        readAvailable -= count
    }

    fun get(): Byte {
        if (readAvailable < 1) throw IOException("Buffer is empty")
        return data[readPos].apply {
            readPos = (readPos + 1) and mask
            readAvailable -= 1
        }
    }

    fun get(v: ByteArray, offset: Int = 0, size: Int = v.size - offset) {
        if (size > readAvailable) throw IOException("Buffer is empty")
        // @TODO: Up to two arraycopy
        for (n in 0 until size) {
            v[offset + n] = data[readPos]
            readPos = (readPos + 1) and mask
        }
        readAvailable -= size
    }

    fun put(v: Byte) {
        if (writeAvailable < 1) throw IOException("Buffer is full")
        data[writePos] = v
        writePos = (writePos + 1) and mask
        readAvailable += 1
    }

    fun put(v: ByteArray, offset: Int = 0, size: Int = v.size - offset) {
        if (size > writeAvailable) throw IOException("Buffer is full")
        // @TODO: Up to two arraycopy
        for (n in 0 until size) {
            data[writePos] = v[offset + n]
            writePos = (writePos + 1) and mask
        }
        readAvailable += size
    }

    fun put(v: ByteBuffer, size: Int) {
        if (size > writeAvailable) throw IOException("Buffer is full")
        // @TODO: Up to two get
        for (n in 0 until size) {
            data[writePos] = v.get()
            writePos = (writePos + 1) and mask
        }
        readAvailable += size
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
        return deferred.await()
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

object Hex {
    val DIGITS = "0123456789ABCDEF"
    val DIGITS_UPPER = DIGITS.toUpperCase()
    val DIGITS_LOWER = DIGITS.toLowerCase()

    fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    fun decode(str: String): ByteArray {
        val out = ByteArray(str.length / 2)
        for (n in 0 until out.size) {
            val n2 = n * 2
            out[n] = (str.substring(n2, n2 + 2).toIntOrNull(16) ?: 0).toByte()
        }
        return out
    }

    fun encode(src: ByteArray): String =
        encodeBase(src, DIGITS_LOWER)

    fun encodeLower(src: ByteArray): String =
        encodeBase(src, DIGITS_LOWER)
    fun encodeUpper(src: ByteArray): String =
        encodeBase(src, DIGITS_UPPER)

    private fun encodeBase(data: ByteArray, digits: String = DIGITS): String {
        val out = StringBuilder(data.size * 2)
        for (n in data.indices) {
            val v = data[n].toInt() and 0xFF
            out.append(digits[(v ushr 4) and 0xF])
            out.append(digits[(v ushr 0) and 0xF])
        }
        return out.toString()
    }
}

val ByteArray.hex: String get() = Hex.encodeLower(this)
val String.hex: ByteArray get() = Hex.decode(this)

typealias CancelHandler = Signal<Unit>

class Signal<T>(val onRegister: () -> Unit = {}) { //: AsyncSequence<T> {
    inner class Node(val once: Boolean, val item: (T) -> Unit) : Closeable {
        override fun close() {
            handlers.remove(this)
        }
    }

    private var handlersToRun = ArrayList<Node>()
    private var handlers = ArrayList<Node>()
    private var handlersNoOnce = ArrayList<Node>()

    val listenerCount: Int get() = handlers.size

    fun once(handler: (T) -> Unit): Closeable = _add(true, handler)
    fun add(handler: (T) -> Unit): Closeable = _add(false, handler)

    fun clear() = handlers.clear()

    private fun _add(once: Boolean, handler: (T) -> Unit): Closeable {
        onRegister()
        val node = Node(once, handler)
        handlers.add(node)
        return node
    }

    operator fun invoke(value: T) {
        val oldHandlers = handlers
        handlersNoOnce.clear()
        handlersToRun.clear()
        for (handler in oldHandlers) {
            handlersToRun.add(handler)
            if (!handler.once) handlersNoOnce.add(handler)
        }
        val temp = handlers
        handlers = handlersNoOnce
        handlersNoOnce = temp

        for (handler in handlersToRun) {
            handler.item(value)
        }
    }

    operator fun invoke(handler: (T) -> Unit): Closeable = add(handler)
}

interface Consumer<T> : Closeable {
    suspend fun consume(cancel: CancelHandler? = null): T?
}

interface Producer<T> : Closeable {
    fun produce(v: T): Unit
}

open class ProduceConsumer<T> : Consumer<T>, Producer<T> {
    private val items = LinkedList<T?>()
    private val consumers = LinkedList<(T?) -> Unit>()
    private var closed = false

    val availableCount get() = synchronized(this) { items.size }

    override fun produce(v: T) {
        synchronized(this) { items.addLast(v) }
        flush()
    }

    override fun close() {
        synchronized(this) {
            items.addLast(null)
            closed = true
        }
        flush()
    }

    private fun flush() {
        while (true) {
            var done = false
            var consumer: ((T?) -> Unit)? = null
            var item: T? = null
            synchronized(this) {
                if (consumers.isNotEmpty() && items.isNotEmpty()) {
                    consumer = consumers.removeFirst()
                    item = items.removeFirst()
                } else {
                    done = true
                }
            }
            if (done) break
            consumer!!(item)
        }
    }

    override suspend fun consume(cancel: CancelHandler?): T? = suspendCoroutine { c ->
        val consumer: (T?) -> Unit = {
            c.resume(it)
            //if (it != null) c.resume(it) else c.resumeWithException(EOFException())
        }
        if (cancel != null) {
            cancel {
                synchronized(this) {
                    consumers -= consumer
                }
                c.resumeWithException(CancellationException(""))
            }
        }
        synchronized(this) {
            consumers += consumer
        }
        flush()
    }
}


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


/*
fun String.toByteArray(): ByteArray {
    val out = ByteArray(this.length)
    for (n in 0 until out.size) out[n] = this[n].toByte()
    return out
}
*/
