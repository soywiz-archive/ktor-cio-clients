package io.ktor.experimental.client.redis

import io.ktor.experimental.client.redis.protocol.Reader
import io.ktor.experimental.client.redis.protocol.Writer
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.net.*
import java.nio.charset.*

class RedisRequest(val args: Any?, val result: CompletableDeferred<ByteReadChannel>)

private const val DEFAULT_PIPELINE_SIZE = 10

/**
 * Rewrite in request consuming way
 */
internal class RedisPipeline(
    private val socketAddress: Socket,
    bufferSize: Int = 0x1000,
    pipelineSize: Int = DEFAULT_PIPELINE_SIZE,
    private val dispatcher: CoroutineDispatcher = DefaultDispatcher
) : Redis {
    override val context: Job = Job()

    private val reader = Reader(charset, bufferSize)
    private val writer = Writer(charset)

    // Common queue is not required align reading because Redis support pipelining : https://redis.io/topics/pipelining
    private val sender = actor<RedisRequest>(
        dispatcher, capacity = pipelineSize, parent = context
    ) {
        val builder = BlobBuilder(bufferSize, charset)

        consumeEach { request ->
            receiver.send(request.result)

            builder.reset()
            writer.writeValue(request.args, builder)
            builder.writeTo(output)
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

    override suspend fun execute(vararg args: Any?): ByteReadChannel {
        val result = CompletableDeferred<ByteReadChannel>()
        sender.send(RedisRequest(args, result))
        return result.await()
    }

    override fun close() {
        context.cancel()
    }
}