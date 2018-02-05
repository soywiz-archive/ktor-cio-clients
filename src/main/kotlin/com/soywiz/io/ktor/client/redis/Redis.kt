package com.soywiz.io.ktor.client.redis

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong

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
