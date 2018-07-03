package io.ktor.experimental.client.redis

import io.ktor.cio.*
import io.ktor.experimental.client.redis.protocol.*
import io.ktor.experimental.client.redis.protocol.Reader
import io.ktor.experimental.client.redis.protocol.Writer
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.io.*
import java.nio.charset.*

internal class RedisRequest(val args: Any?, val result: CompletableDeferred<ByteReadChannel>)

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

    private val reader = Reader(charset)

    val context: Job = launch(dispatcher) {
        requestQueue.consumeEach { request ->
            receiver.send(request.result)

            output.writePacket {
                writeRedisValue(request.args, charset = charset)
            }

            output.flush()
        }
    }

    private val receiver = actor<CompletableDeferred<ByteReadChannel>>(
        dispatcher, capacity = pipelineSize, parent = context
    ) {
        consumeEach { result ->
            try {
                val response = reader.readValue(input) as ByteReadChannel // TODO("change reader/writer")
                result.complete(response)
            } catch (cause: Throwable) {
                result.completeExceptionally(cause)
            }
        }
    }

    override fun close() {
        context.cancel()
    }
}