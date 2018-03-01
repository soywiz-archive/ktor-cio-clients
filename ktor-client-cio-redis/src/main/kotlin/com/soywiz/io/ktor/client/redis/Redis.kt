package com.soywiz.io.ktor.client.redis

import com.soywiz.io.ktor.client.util.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.runBlocking
import java.io.*
import java.net.*
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong

//private const val DEBUG = true
private const val DEBUG = false

// https://redis.io/topics/protocol
class Redis(val maxConnections: Int = 50, val stats: Stats = Stats(), private val clientFactory: suspend () -> Client) :
    RedisCommand {
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
            return Redis(maxConnections, stats) {
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

        private suspend fun readValue(): Any? {
            val reader = reader()
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
                        reader.readShort() // CR LF
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

        private inline fun debug(msg: () -> String) {
            if (DEBUG) println(msg())
        }

        override suspend fun commandAny(vararg args: Any?): Any? {
            val writer = writer()
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

internal object RedisExperiment {
    @JvmStatic
    fun main(args: Array<String>): Unit {
        runBlocking {
            val redis = Redis(bufferSize = 1)
            //val redis = Redis(bufferSize = 3)
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
