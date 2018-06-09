package io.ktor.experimental.test.host

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.nio.charset.*
import java.util.*

// @TODO: Move to Ktor
val Url.fullUrl: String
    get() {
        val otherPort = this.port != this.protocol.defaultPort
        return this.protocol.name + "://" + (if (otherPort) this.hostWithPort else this.host) + this.fullPath
    }

suspend fun ByteReadChannel.readAsBytes() = readRemaining().readBytes()
suspend fun ByteReadChannel.readAsString(charset: Charset = Charsets.UTF_8) = readAsBytes().toString(charset)

fun OutgoingContent.read(range: LongRange? = null): ByteReadChannel {
    return when (this) {
        is OutgoingContent.NoContent -> kotlinx.coroutines.experimental.io.ByteReadChannel(kotlin.byteArrayOf())
        is OutgoingContent.ByteArrayContent ->
            if (range != null) {
                kotlinx.coroutines.experimental.io.ByteReadChannel(
                    this.bytes().copyOfRange(
                        range.start.toInt(),
                        (range.endInclusive + 1).toInt()
                    )
                )
            } else {
                kotlinx.coroutines.experimental.io.ByteReadChannel(this.bytes())
            }
        is OutgoingContent.ReadChannelContent -> if (range != null) this.readFrom(range) else this.readFrom()
        is OutgoingContent.WriteChannelContent -> kotlinx.coroutines.experimental.io.ByteReadChannel(kotlin.byteArrayOf())
        is OutgoingContent.ProtocolUpgrade -> kotlinx.coroutines.experimental.io.ByteReadChannel(kotlin.byteArrayOf())
    }
}

class LogHttpClient {
    private val log = ArrayList<String>()
    val client = HttpClient(object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return object : HttpClientEngine {
                override val dispatcher: CoroutineDispatcher = DefaultDispatcher
                override fun close() = Unit
                override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
                    val request = object : HttpRequest {
                        override val call: HttpClientCall = call
                        override val attributes: Attributes = Attributes()
                        override val method: HttpMethod = data.method
                        override val url: Url = data.url
                        override val headers: Headers = data.headers
                        override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()
                        override val content: OutgoingContent = data.body as OutgoingContent
                    }
                    log += listOf(
                        request.method.value,
                        request.url.fullUrl,
                        request.headers,
                        request.content.read().readAsString()
                    ).joinToString(", ")

                    val response = request(request)
                    return HttpEngineCall(request, object : HttpResponse {
                        override val call: HttpClientCall = call
                        override val status: HttpStatusCode = response.status
                        override val headers: Headers = response.headers
                        override val requestTime = Date()
                        override val responseTime = Date()
                        override val version = HttpProtocolVersion.HTTP_1_1
                        override val content: ByteReadChannel = response.content
                        override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()
                        override fun close() {
                            executionContext.complete(Unit)
                            @Suppress("UNCHECKED_CAST")
                            (call.request.executionContext as CompletableDeferred<Unit>).complete(Unit)
                        }

                    })
                }
            }
        }
    })

    var request: suspend (request: HttpRequest) -> Response = { request -> defaultResponse }

    var defaultResponse = Response(
        HttpStatusCode.OK,
        Headers.build { },
        ByteReadChannel("OK".toByteArray())
    )

    data class Response(val status: HttpStatusCode, val headers: Headers, val content: ByteReadChannel) {
        fun withStringResponse(str: String, charset: Charset = Charsets.UTF_8) =
            copy(content = ByteReadChannel(str.toByteArray(charset)))
    }

    fun getAndClearLog(): List<String> = log.toList().apply { log.clear() }
}
