package io.ktor.experimental.client.util.sync

import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.io.core.*
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.atomic.*

private class PipelineElement<TRequest : Any, TResponse : Any>(
    val request: TRequest,
    val response: CompletableDeferred<TResponse>
)

abstract class ConnectionPipeline<TRequest : Any, TResponse : Any>(
    pipelineSize: Int = 10
) : Closeable {
    private val started = AtomicBoolean(false)
    open val context: Job = Job()

    private val writer = actor<PipelineElement<TRequest, TResponse>>(
        capacity = pipelineSize,
        parent = context,
        start = CoroutineStart.LAZY
    ) {
        try {
            onStart()
            for (element in channel) {
                try {
                    reader.send(element)
                } catch (cause: Throwable) {
                    val origin = cause.withRequest(element.request)
                    element.response.completeExceptionally(origin)
                    throw cause
                }

                send(element.request)
            }
        } catch (cause: Throwable) {
            reader.close(cause)
            onError(cause)

            try {
                consumeEach { it.response.completeExceptionally(cause) }
            } catch (cause: Throwable) {
            }
        } finally {
            reader.close()
            try {
                onDone()
            } catch (_: Throwable) {
            }
        }
    }

    private val reader = actor<PipelineElement<TRequest, TResponse>>(
        capacity = pipelineSize,
        parent = context,
        start = CoroutineStart.LAZY
    ) {
        for (element in channel) {
            element.response.completeWith {
                try {
                    this@ConnectionPipeline.receive()
                } catch (cause: Throwable) {
                    throw cause.withRequest(element.request)
                }
            }
        }
    }

    protected abstract suspend fun send(request: TRequest)

    protected abstract suspend fun receive(): TResponse

    protected open suspend fun onStart() {}
    protected open fun onDone() {}
    protected open fun onError(cause: Throwable) {}

    suspend fun query(request: TRequest): TResponse {
        val result = CompletableDeferred<TResponse>()
        writer.send(PipelineElement(request, result))
        return result.await()
    }

    override fun close() {
        writer.close()
    }
}

class PipelineException(val request: Any, override val cause: Throwable): RuntimeException() {
    override val message: String = "Fail to execute: $request"
}

private fun Throwable.withRequest(request: Any): Throwable = PipelineException(request, this)

