package com.soywiz.io.ktor.client.amazon.s3

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
import kotlin.test.*

class S3Test {
    val httpClient = LogHttpClient()
    val timeProvider = { 1486426548734L }
    lateinit var s3: S3

    init {
        runBlocking {
            s3 = S3(
                region = "demo",
                accessKey = "myaccesskey",
                secretKey = "mysecretKey",
                httpClient = httpClient.client,
                timeProvider = timeProvider
            )
        }
    }

    @Test
    fun checkGet() {
        runBlocking {
            httpClient.defaultResponse = httpClient.defaultResponse.withStringResponse("hello")

            assertEquals("hello", s3.bucket("test").file("hello.txt").readString())
            assertEquals(
                listOf("GET, https://test.s3-demo.amazonaws.com/hello.txt, Headers [date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:I/jL9Lkq+n6DT0ZuLmK71B/wABQ=]], "),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkPut() {
        runBlocking {
            s3.bucket("test").file("hello.json").writeString("hello")
            assertEquals(
                listOf("PUT, https://test.s3-demo.amazonaws.com/hello.json, Headers [Content-Type=[application/json], Content-Length=[5], x-amz-acl=[private], date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:lzucas2uhwPa2vsVJoRzta6RAtg=]], hello"),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkPut2() {
        runBlocking {
            s3.file("test", "hello.txt")
                .writeString("hello", contentType = ContentType.Image.JPEG, access = S3.ACL.PUBLIC_READ)
            assertEquals(
                listOf("PUT, https://test.s3-demo.amazonaws.com/hello.txt, Headers [Content-Type=[image/jpeg], Content-Length=[5], x-amz-acl=[public-read], date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:DceOjup5BapxMUuh6Ww07viLyxg=]], hello"),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkAbsoluteUrl() {
        runBlocking {
            assertEquals("https://.s3-demo.amazonaws.com/", s3.url)
            assertEquals("https://test.s3-demo.amazonaws.com/", s3.bucket("test").url)
            assertEquals("https://hello.s3-demo.amazonaws.com/world", s3.bucket("hello").file("world").url)
        }
    }
}

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
        is OutgoingContent.NoContent -> ByteReadChannel(byteArrayOf())
        is OutgoingContent.ByteArrayContent ->
            if (range != null) {
                ByteReadChannel(this.bytes().copyOfRange(range.start.toInt(), (range.endInclusive + 1).toInt()))
            } else {
                ByteReadChannel(this.bytes())
            }
        is OutgoingContent.ReadChannelContent -> if (range != null) this.readFrom(range) else this.readFrom()
        is OutgoingContent.WriteChannelContent -> ByteReadChannel(byteArrayOf())
        is OutgoingContent.ProtocolUpgrade -> ByteReadChannel(byteArrayOf())
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

    var request: suspend (request: HttpRequest) -> Response = { request ->
        log += listOf(request.method.value, request.url.fullUrl, request.headers, request.content.read().readAsString()).joinToString(", ")
        defaultResponse
    }

    var defaultResponse = Response(
        HttpStatusCode.OK,
        Headers.build { },
        ByteReadChannel("OK".toByteArray())
    )

    data class Response(val status: HttpStatusCode, val headers: Headers, val content: ByteReadChannel) {
        fun withStringResponse(str: String, charset: Charset = Charsets.UTF_8) = copy(content = ByteReadChannel(str.toByteArray(charset)))
    }

    fun getAndClearLog(): List<String> = log.toList().apply { log.clear() }
}
