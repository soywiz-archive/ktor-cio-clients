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
import kotlinx.io.core.*
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

    fun getURL(bucket: String, key: String) = endpointPattern.replace("{bucket}", bucket).replace("{key}", key)
    val url get() = getURL("", "")

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
            Stat(
                exists = true,
                length = Dynamic { result.headers["content-length"].long },
                contentType = result.headers["content-type"]?.let { ContentType.parse(it) }
            )
        } else {
            Stat(exists = false)
        }
    }

    data class Stat(
        val exists: Boolean,
        val length: Long = 0L,
        val contentType: ContentType? = null
    )

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
        contentType: ContentType? = null
    ): Long {
        val length = content.contentLength ?: error("contentLength must be set for content")
        request(
            HttpMethod.Put,
            path,
            headers = Headers.build {
                contentType(contentType ?: ContentType.defaultForFilePath(path))
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
            //println(npath.url)
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

class S3Bucket(val s3: S3, val bucket: String) {
    val url get() = s3.getURL(bucket, "")

    private fun getPath(file: String) = "$bucket/$file"
    suspend fun put(
        file: String,
        content: OutgoingContent,
        access: S3.ACL = S3.ACL.PRIVATE,
        contentType: ContentType? = null
    ) = s3.put(getPath(file), content, access, contentType)

    suspend fun stat(file: String) = s3.stat(getPath(file))
    suspend fun get(file: String, range: LongRange? = null) = s3.get(getPath(file), range)
}

class S3File(val bucket: S3Bucket, val file: String) {
    val url get() = bucket.s3.getURL(bucket.bucket, file)

    suspend fun put(
        content: OutgoingContent,
        access: S3.ACL = S3.ACL.PRIVATE,
        contentType: ContentType? = null
    ) = bucket.put(file, content, access, contentType)

    suspend fun stat() = bucket.stat(file)
    suspend fun get(range: LongRange? = null) = bucket.get(file, range)

    suspend fun readBytes() = get().readRemaining().readBytes()
    suspend fun readString(charset: Charset = Charsets.UTF_8) = readBytes().toString(charset)

    suspend fun writeBytes(content: ByteArray, contentType: ContentType? = null, access: S3.ACL = S3.ACL.PRIVATE) = put(ByteArrayContentWithLength(content), contentType = contentType, access = access)
    suspend fun writeString(content: String, charset: Charset = Charsets.UTF_8, contentType: ContentType? = null, access: S3.ACL = S3.ACL.PRIVATE) = writeBytes(content.toByteArray(charset), contentType = contentType, access = access)
}

fun S3Bucket.file(name: String) = S3File(this, name)
fun S3.bucket(name: String) = S3Bucket(this, name)
fun S3.file(bucket: String, key: String) = bucket(bucket).file(key)

private fun Headers.withReplaceHeaders(vararg items: Pair<String, String>): Headers {
    return Headers.build {
        appendAll(this@withReplaceHeaders)
        for ((key, value) in items) set(key, value)
    }
}

// @TODO: ByteArrayContent should provide contentLength
class ByteArrayContentWithLength(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long = bytes.size.toLong()
    override fun bytes(): ByteArray = bytes
}

fun ByteArrayContentWithLength(
    str: String,
    charset: Charset = Charsets.UTF_8
): ByteArrayContentWithLength = ByteArrayContentWithLength(str.toByteArray(charset))
