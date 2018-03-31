package com.soywiz.io.ktor.client.redis

import com.soywiz.io.ktor.client.util.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteBuffer
import java.io.*
import java.lang.StringBuilder
import java.net.*
import java.nio.*
import java.nio.charset.*
import java.util.concurrent.atomic.AtomicLong

//private const val DEBUG = true
private const val DEBUG = false

// https://redis.io/topics/protocol
class Redis(
    val maxConnections: Int = 50,
    val stats: Stats = Stats(),
    val charset: Charset = Charsets.UTF_8,
    private val clientFactory: suspend () -> Client
) : RedisCommand {
    companion object {
        //suspend operator fun invoke(
        operator fun invoke(
            addresses: List<SocketAddress> = listOf(InetSocketAddress("127.0.0.1", 6379)),
            maxConnections: Int = 50,
            charset: Charset = Charsets.UTF_8,
            password: String? = null,
            stats: Stats = Stats(),
            bufferSize: Int = 0x1000
        ): Redis {
            var index: Int = 0
            return Redis(maxConnections, stats, charset) {
                val tcpClientFactory = aSocket().tcp()
                //Console.log("tcpClient: ${tcpClient::class}")
                Client(
                    reconnect = { client ->
                        index = (index + 1) % addresses.size
                        val host = addresses[index] // Round Robin
                        val socket = tcpClientFactory.connect(host)
                        if (password != null) client.auth(password)
                        Pipes(socket.openReadChannel(), socket.openWriteChannel(autoFlush = true), socket)
                    },
                    charset = charset,
                    stats = stats
                ).apply {
                    //init()
                }
            }
        }

        //private const val CR = '\r'.toByte()
        private const val LF = '\n'.toByte()
        private val LF_BB = ByteBuffer.wrap(byteArrayOf(LF))
    }

    class Stats {
        val commandsQueued = AtomicLong()
        val commandsStarted = AtomicLong()
        val commandsPreWritten = AtomicLong()
        val commandsWritten = AtomicLong()
        val commandsErrored = AtomicLong()
        val commandsFailed = AtomicLong()
        val commandsFinished = AtomicLong()

        override fun toString(): String {
            return "Stats(commandsQueued=$commandsQueued, commandsStarted=$commandsStarted, commandsPreWritten=$commandsPreWritten, commandsWritten=$commandsWritten, commandsErrored=$commandsErrored, commandsFinished=$commandsFinished)"
        }
    }

    data class Pipes(
        val reader: ByteReadChannel,
        val writer: ByteWriteChannel,
        val closeable: Closeable
    )

    class Client(
        private val charset: Charset = Charsets.UTF_8,
        private val stats: Stats = Stats(),
        private val reconnect: suspend (Client) -> Pipes
    ) : RedisCommand {
        val charsetDecoder = charset.newDecoder()
        lateinit var pipes: Pipes
        private val initOnce = OnceAsync()
        private val commandQueue = AsyncQueue()

        companion object {
            val MAX_RETRIES = 10
        }

        suspend fun reader(): ByteReadChannel = initOnce().reader
        suspend fun writer(): ByteWriteChannel = initOnce().writer
        suspend fun closeable(): Closeable = initOnce().closeable

        suspend fun reconnect() {
            try {
                pipes = reconnect(this@Client)
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
                        throw RuntimeException("Giving up trying to connect to redis max retries: $MAX_RETRIES")
                    }
                }
            }
        }

        private suspend fun initOnce(): Pipes {
            initOnce {
                commandQueue {
                    reconnectRetrying()
                }
            }
            return pipes
        }

        suspend fun close() {
            closeable().close()
        }

        private val valueSB = StringBuilder(1024)
        private val valueCB = CharBuffer.allocate(1024)
        private val valueBB = ByteBuffer.allocate((1024 / charsetDecoder.maxCharsPerByte()).toInt())
        private val tempCRLF = ByteArray(2)
        private suspend fun readValue(): Any? {
            val reader = reader()

            valueSB.setLength(0)
            val line = reader.readUntilString(
                valueSB, LF_BB, charsetDecoder, valueCB, valueBB
            ).trimEnd().toString()

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
                        reader.readFully(tempCRLF) // CR LF
                        //reader.readShort() // CR LF
                        data.toString(charset).apply {
                            debug { "Redis[RECV][data]: $this" }
                        }
                    }
                }
                '*' -> { // Array reply
                    val arraySize = line.substring(1).toInt()
                    (0 until arraySize).map { readValue() }
                }
                else -> throw ResponseException("Unknown param type '${line[0]}'")
            }
        }

        private inline fun debug(msg: () -> String) {
            if (DEBUG) println(msg())
        }

        private val cmdChunk = BOS(1024, charset)
        private val cmd = BOS(1024, charset)
        override suspend fun commandAny(vararg args: Any?): Any? {
            val writer = writer()
            //println(args.toList())
            stats.commandsQueued.incrementAndGet()
            return commandQueue {
                cmd.reset()
                cmd.append('*')
                cmd.append(args.size)
                cmd.append('\r')
                cmd.append('\n')
                for (arg in args) {
                    cmdChunk.reset()
                    when (arg) {
                        is Int -> cmdChunk.append(arg)
                        is Long -> cmdChunk.append(arg)
                        else -> cmdChunk.append(arg.toString())
                    }
                    // Length of the argument.
                    cmd.append('$')
                    cmd.append(cmdChunk.size())
                    cmd.append('\r')
                    cmd.append('\n')
                    cmd.append(cmdChunk)
                    cmd.append('\r')
                    cmd.append('\n')
                }

                // Common queue is not required align reading because Redis support pipelining : https://redis.io/topics/pipelining
                val data = cmd.buf()
                val dataLen = cmd.size()
                var retryCount = 0

                debug { "Redis[SEND]: $cmd" }

                retry@ while (true) {
                    stats.commandsStarted.incrementAndGet()
                    try {
                        stats.commandsPreWritten.incrementAndGet()
                        //Console.log("writer: $writer")
                        //Console.log("writer: ${writer::class}")
                        //Console.log(writer)
                        //println("[a]")
                        writer.writeFully(data, 0, dataLen) // @TODO: Maybe use writeAvailable instead of appending?
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
                            reconnect()
                        } catch (e: Throwable) {
                        }
                        delay(500 * retryCount)
                        retryCount++
                        if (retryCount < MAX_RETRIES) {
                            continue@retry
                        } else {
                            throw RuntimeException("Giving up with this redis request max retries $MAX_RETRIES")
                        }
                    } catch (t: ResponseException) {
                        stats.commandsFailed.incrementAndGet()
                        //println(t)
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

/**
 * Optimized class for allocation free String/ByteArray building
 */
private class BOS(size: Int, val charset: Charset) : ByteArrayOutputStream(size) {
    private val charsetEncoder = charset.newEncoder()
    private val tempCB = CharBuffer.allocate(1024)
    private val tempBB = ByteBuffer.allocate((tempCB.count() * charsetEncoder.maxBytesPerChar()).toInt())
    private val tempSB = StringBuilder(64)

    fun buf(): ByteArray {
        return buf
    }

    fun append(value: Long) {
        tempSB.setLength(0)
        tempSB.append(value)
        tempCB.clear()
        tempSB.getChars(0, tempSB.length, tempCB.array(), 0)
        //println(tempCB.toString())
        tempCB.position(tempSB.length)
        tempCB.flip()
        //println(tempCB.remaining())
        append(tempCB)
    }

    fun append(value: Int) {
        when (value) {
            in 0..9 -> {
                write(('0' + value).toInt())
            }
            in 10..99 -> {
                write(('0' + (value / 10)).toInt())
                write(('0' + (value % 10)).toInt())
            }
            else -> {
                //append("$value")
                tempSB.setLength(0)
                tempSB.append(value)
                tempCB.clear()
                tempSB.getChars(0, tempSB.length, tempCB.array(), 0)
                //println(tempCB.toString())
                tempCB.position(tempSB.length)
                tempCB.flip()
                //println(tempCB.remaining())
                append(tempCB)
            }
        }
    }

    fun append(char: Char) {
        if (char.toInt() <= 0xFF) {
            write(char.toInt())
        } else {
            tempCB.clear()
            tempCB.put(char)
            tempCB.flip()
            append(tempCB)
        }
    }

    fun append(str: String) {
        val len = str.length
        if (len == 0) return

        val chunk = Math.min(len, 1024)

        for (n in 0 until len step chunk) {
            tempCB.clear()
            val cend = Math.min(len, n + chunk)
            str.toCharArray(tempCB.array(), 0, n, cend)
            tempCB.position(cend - n)
            tempCB.flip()
            append(tempCB)
        }
    }

    fun append(bb: ByteBuffer) {
        while (bb.hasRemaining()) {
            write(bb.get().toInt())
        }
    }

    fun append(cb: CharBuffer) {
        charsetEncoder.reset()
        while (cb.hasRemaining()) {
            tempBB.clear()
            charsetEncoder.encode(cb, tempBB, false)
            tempBB.flip()
            append(tempBB)
        }
        tempBB.clear()
        charsetEncoder.encode(cb, tempBB, true)
        tempBB.flip()
        append(tempBB)
    }

    fun append(that: BOS) {
        this.write(that.buf(), 0, that.size())
    }

    override fun toString() = toByteArray().toString(charset)
}
