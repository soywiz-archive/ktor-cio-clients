package io.ktor.experimental.client.redis

import io.ktor.experimental.client.redis.protocol.Reader
import io.ktor.experimental.client.redis.protocol.Writer
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.nio.charset.*
import java.util.concurrent.atomic.*

class RedisSubscription private constructor(
    private val pipes: Redis.Pipes,
    private val psubscriptions: AtomicLong,
    val messages: ReceiveChannel<Message>,
    val charset: Charset
) : Closeable, ReceiveChannel<RedisSubscription.Message> by messages {
    val subscriptions get() = psubscriptions.get()

    private val respWriter = Writer(charset)

    data class Message(val pattern: String?, val channel: String, val content: String)

    companion object {
        suspend fun open(pipes: Redis.Pipes, charset: Charset): RedisSubscription {
            val respReader = Reader(charset)
            val psubscriptions = AtomicLong(0L)

            return RedisSubscription(pipes, psubscriptions, produce {
                while (true) {
                    val info = respReader.readValue(pipes.reader) as List<Any?>
                    when (info.firstOrNull()) {
                        "message" -> {
                            channel.send(
                                Message(
                                    null,
                                    info[1].toString(),
                                    info[2].toString()
                                )
                            )
                        }
                        "pmessage" -> {
                            channel.send(
                                Message(
                                    info[1].toString(),
                                    info[2].toString(),
                                    info[3].toString()
                                )
                            )
                        }
                        "subscribe", "unsubscribe", "psubscribe", "punsubscribe" -> {
                            val channel = info[1].toString()
                            psubscriptions.set(info[2] as Long)
                        }
                        "pong" -> {
                        }
                    }
                }
            }, charset)
        }
    }

    suspend private fun command(vararg args: String) = respWriter.writeValue(args, pipes.writer)

    suspend fun ping() = this.apply { command("ping") }

    suspend fun subscribe(vararg channels: String) = this.apply { command("subscribe", *channels) }
    suspend fun psubscribe(vararg patterns: String) = this.apply { command("psubscribe", *patterns) }
    suspend fun unsubscribe(vararg channels: String) = this.apply { command("unsubscribe", *channels) }
    suspend fun punsubscribe(vararg patterns: String) = this.apply { command("punsubscribe", *patterns) }

    override fun close() {
        messages.cancel()
        pipes.closeable.close()
    }
}

/**
 * Posts a message to the given channel.
 *
 * Returns the number of clients that received the message.
 */
suspend fun Redis.publish(channel: String, message: String): Long = commandLong("publish", channel, message)

/**
 * Subscribes the client to the specified channels.
 *
 * Once the client enters the subscribed state it is not supposed to issue any other commands,
 * except for additional SUBSCRIBE, PSUBSCRIBE, UNSUBSCRIBE and PUNSUBSCRIBE commands.
 */
suspend fun Redis.subscribe(vararg channels: String): RedisSubscription =
    RedisSubscription.open(capturePipes(), charset).subscribe(*channels)

/**
 * Subscribes the client to the given patterns.
 *
 * Supported glob-style patterns:
 *
 * h?llo subscribes to hello, hallo and hxllo
 * h*llo subscribes to hllo and heeeello
 * h[ae]llo subscribes to hello and hallo, but not hillo
 * Use \ to escape special characters if you want to match them verbatim.
 */
suspend fun Redis.psubscribe(vararg patterns: String): RedisSubscription =
    RedisSubscription.open(capturePipes(), charset).psubscribe(*patterns)

/**
 * Returns the number of subscriptions to patterns (that are performed using the PSUBSCRIBE command).
 * Note that this is not just the count of clients subscribed to patterns but the total
 * number of patterns all the clients are subscribed to.
 *
 * Return value
 * Integer reply: the number of patterns all the clients are subscribed to.
 */
suspend fun Redis.pubSubNumPat(): Long = commandLong("pubsub", "numpat")

/**
 * Returns the number of subscribers (not counting clients subscribed to patterns) for the specified channels.
 *
 * Return value
 * Array reply: a list of channels and number of subscribers for every channel.
 * The format is channel, count, channel, count, ..., so the list is flat.
 * The order in which the channels are listed is the same as the order of the channels specified in the command call.
 * Note that it is valid to call this command without channels. In this case it will just return an empty list.
 */
suspend fun Redis.pubSubNumSub(vararg channels: String): List<Long> = commandArrayLong("pubsub", "numsub", *channels)

/**
 * Lists the currently active channels. An active channel is a Pub/Sub channel with one or more subscribers
 * (not including clients subscribed to patterns).
 *
 * If no pattern is specified, all the channels are listed, otherwise if pattern is specified only channels
 * matching the specified glob-style pattern are listed.
 *
 * Return value
 * Array reply: a list of active channels, optionally matching the specified pattern.
 */
suspend fun Redis.pubSubChannels(pattern: String? = null): List<String> =
    commandArrayString("pubsub", "channels") + arrayOf(pattern).filterNotNull()
