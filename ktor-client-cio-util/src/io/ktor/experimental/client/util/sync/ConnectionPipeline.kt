package io.ktor.experimental.client.util.sync

import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.io.core.*

private class PipelineElement<TRequest : Any, TResponse : Any>(
    val request: TRequest,
    val response: CompletableDeferred<TResponse>
)

abstract class ConnectionPipeline<TRequest : Any, TResponse : Any> : Closeable {
    open val context: Job = Job()

    private val writer = actor<PipelineElement<TRequest, TResponse>>(
        parent = context,
        start = CoroutineStart.LAZY
    ) {
        try {
            onStart()
            for (element in channel) {
                reader.send(element.response)
                send(element.request)
            }
        } catch (cause: Throwable) {
            reader.close(cause)
            onError(cause)
        } finally {
            reader.close()
            onDone()
        }
    }

    private val reader = actor<CompletableDeferred<TResponse>>(
        parent = context,
        start = CoroutineStart.LAZY
    ) {
        for (element in channel) {
            element.completeWith { this@ConnectionPipeline.receive() }
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