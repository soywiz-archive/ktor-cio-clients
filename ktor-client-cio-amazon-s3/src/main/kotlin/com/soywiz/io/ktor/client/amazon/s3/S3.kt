package com.soywiz.io.ktor.client.amazon.s3

import com.soywiz.io.ktor.client.amazon.auth.*
import com.soywiz.io.ktor.client.util.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.nio.charset.*

// http://docs.amazonwebservices.com/AmazonS3/latest/dev/RESTAuthentication.html#ConstructingTheAuthenticationHeader
// https://github.com/jubos/fake-s3
class S3(
    val credentials: AmazonAuth.Credentials?,
    val endpointPattern: String,
    val httpClient: HttpClient,
    val timeProvider: () -> Long
) {
    companion object {
        suspend operator fun invoke(
            endpoint: String = "https://{bucket}.s3-{region}.amazonaws.com/{key}",
            region: String = System.getenv("AWS_DEFAULT_REGION") ?: "eu-west-1",
            accessKey: String? = null,
            secretKey: String? = null,
            httpClient: HttpClient = HttpClient(Apache),
            timeProvider: () -> Long = { System.currentTimeMillis() }
        ): S3 {
            return S3(
                AmazonAuth.getCredentials(accessKey, secretKey),
                endpoint.replace("{region}", region),
                httpClient,
                timeProvider
            )
        }
    }

    data class ParsedPath(val bucket: String, val key: String, val endpointPattern: String) {
        val url = endpointPattern.replace("{bucket}", bucket).replace("{key}", key)
        val cannonical = "/$bucket/$key"
    }

    private fun normalizePath(path: String) = "/" + path.trim('/')

    class ACL(var text: String) {
        companion object {
            val PRIVATE = ACL("private")
            val PUBLIC_READ = ACL("public-read")
            val PUBLIC_READ_WRITE = ACL("public-read-write")
            val AWS_EXEC_READ = ACL("aws-exec-read")
            val AUTHENTICATED_READ = ACL("authenticated-read")
            val BUCKET_OWNER_READ = ACL("bucket-owner-read")
            val BUCKET_OWNER_FULL_CONTROL = ACL("bucket-owner-full-control")
            val LOG_DELIVERY_WRITE = ACL("log-delivery-write")
        }
    }

    suspend fun stat(path: String): Stat {
        val result = request(HttpMethod.Head, path)

        return if (result.status.isSuccess()) {
            Stat(exists = true, length = Dynamic { result.headers["content-length"].long })
        } else {
            Stat(exists = false)
        }
    }

    data class Stat(val exists: Boolean, val length: Long = 0L)

    suspend fun get(
        path: String,
        range: LongRange? = null
    ): ByteReadChannel {
        return request(HttpMethod.Get, path, Headers.build {
            if (range != null) {
                set("Range", "bytes=${range.start}-${range.endInclusive}")
            }
        }).content
    }

    suspend fun put(
        path: String,
        content: OutgoingContent,
        access: ACL = ACL.PRIVATE,
        contentType: ContentType = ContentType.defaultForFilePath(path)
    ): Long {
        val length = content.contentLength ?: error("contentLength must be set for content")
        request(
            HttpMethod.Put,
            path,
            headers = Headers.build {
                contentType(contentType)
                contentLength(length)
                set("x-amz-acl", access.text)
            },
            content = content
        )

        return length
    }

    suspend fun request(
        method: HttpMethod,
        path: String,
        headers: Headers = Headers.build { },
        content: OutgoingContent? = null
    ): HttpResponse {
        val npath = parsePath(path)
        val mheaders = genHeaders(
            method, npath, headers.withReplaceHeaders(
                "date" to AmazonAuth.V1.DATE_FORMAT.format(timeProvider())
            )
        )
        return httpClient.call {
            this.method = method
            println(npath.url)
            this.url(npath.url)
            this.headers { appendAll(mheaders) }
            if (content != null) {
                this.body = content
            }
        }.response
    }

    private fun parsePath(path: String): ParsedPath {
        val npath = path.trim('/')
        val parts = npath.split('/', limit = 2)
        return ParsedPath(parts[0].trim('/'), parts.getOrElse(1) { "" }.trim('/'), endpointPattern)
    }

    private suspend fun genHeaders(
        method: HttpMethod,
        path: ParsedPath,
        headers: Headers = Headers.build { }
    ): Headers = if (credentials != null) {
        headers.withReplaceHeaders(
            "Authorization" to AmazonAuth.V1.getAuthorization(
                credentials.accessKey,
                credentials.secretKey,
                method,
                path.cannonical,
                headers
            )
        )
    } else {
        headers
    }
}

private fun Headers.withReplaceHeaders(vararg items: Pair<String, String>): Headers {
    return Headers.build {
        appendAll(this@withReplaceHeaders)
        for ((key, value) in items) set(key, value)
    }
}