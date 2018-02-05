package com.soywiz.io.ktor.client.redis

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.coroutines.experimental.suspendCoroutine

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
    private val cb = CircularByteArray(32 - Integer.numberOfLeadingZeros(bufferSize))
    private val buffer = ByteBuffer.allocate(cb.writeAvailable)

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
