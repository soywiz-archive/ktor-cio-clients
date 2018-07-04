package io.ktor.experimental.client.redis

import io.ktor.experimental.client.redis.protocol.*
import io.ktor.experimental.client.util.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.nio.charset.*

internal class RedisRequest(val args: Any?, val result: CompletableDeferred<Any?>)

private const val DEFAULT_PIPELINE_SIZE = 10

/**
 * Redis connection pipeline
 * https://redis.io/topics/pipelining
 */
internal class RedisPipeline(
    socket: Socket,
    private val requestQueue: Channel<RedisRequest>,
    private val charset: Charset = Charsets.UTF_8,
    pipelineSize: Int = DEFAULT_PIPELINE_SIZE,
    dispatcher: CoroutineDispatcher = DefaultDispatcher
) : Closeable {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel()

    val context: Job = launch(dispatcher) {
        requestQueue.consumeEach { request ->
            receiver.send(request.result)

            output.writePacket {
                writeRedisValue(request.args, charset = charset)
            }

            output.flush()
        }
    }

    private val receiver = actor<CompletableDeferred<Any?>>(
        dispatcher, capacity = pipelineSize, parent = context
    ) {
        val decoder = charset.newDecoder()!!

        consumeEach { result ->
            completeWith(result) {
                input.readRedisMessage(decoder)
            }
        }
    }

    override fun close() {
        context.cancel()
    }
}